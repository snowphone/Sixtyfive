#!python
import argparse
import json
import logging
import os
from shutil import copy, make_archive, rmtree, unpack_archive
from typing import List

from psutil import Process, process_iter, wait_procs
from requests import Response, post


class SavefileManager:
	TOKEN = "m9BGWnHvSw0AAAAAAAAAAdBGNzWtnFBRbPz7yknRC9anv9IKMjU7rEdw5ifv3k7V"
	URL = "https://content.dropboxapi.com/2/files"

	BASE_HEADER = {
	    "Authorization": f"Bearer {TOKEN}",
	    "Content-Type": "application/octet-stream",
	}

	def __init__(self, configs_name):
		configs = json.loads(self._download(configs_name))

		self.backup_root: str = configs["backup_location"]
		self.configs: List[dict] = configs["applications"]
		self.names: List[str] = [conf["name"] for conf in self.configs]
		return

	def _download(self, name: str):
		header = {
		    **self.BASE_HEADER, "Dropbox-API-Arg":
		    json.dumps({
		        "path": f"/{name}",
		    })
		}
		resp: Response = post(f"{self.URL}/download", headers=header)
		resp.raise_for_status()
		return resp.content

	def watch(self):
		print(f"Watching {self.names}...")
		while True:
			try:
				proc: Process = next(
				    proc for proc in process_iter(attrs=["pid", "name"])
				    if proc.info["name"] in self.names)
				name = proc.info["name"]

				self.names.remove(name)
				print(f"{name} started")
				self._register_process(proc)
			except StopIteration:
				continue

	def restore(self, proc_name):
		try:
			config: dict = next(conf for conf in self.configs
			                    if conf["name"] == proc_name)
		except StopIteration as e:
			raise RuntimeError(
			    f"{proc_name} does not exist in the configuration")

		archive_name = proc_name.replace('.exe', '.zip')
		print(f"Downloading data of {proc_name}...", end=' ')
		data = self._download(f"data/{archive_name}")

		with open(archive_name, "wb") as f:
			f.write(data)

		restore_path = config["save_path"]
		unpack_archive(archive_name, restore_path, "zip")

		os.remove(archive_name)
		print(f"Restoration complete!")
		return

	def _register_process(self, proc: Process):
		wait_procs([proc], callback=self._backup_savefile)
		return

	def _backup_savefile(self, proc: Process):
		proc_name = proc.info["name"]
		self.names.append(proc_name)

		src_path: str = next(config["save_path"] for config in self.configs
		                     if config["name"] == proc_name)

		print(f"{proc_name} is terminated. Backup savefiles...", end=' ')
		make_archive(src_path[:-4], "zip", src_path)

		archive_path = src_path + ".zip"
		self._upload(archive_path, proc_name)
		rmtree(archive_path)
		return

	def _upload(self, data_path: str, proc_name: str):
		header = {
		    **self.BASE_HEADER, "Dropbox-API-Arg":
		    json.dumps({
		        "path": f"data/{proc_name[:-4]}.zip",
		        "mode": {
		            ".tag": "overwrite"
		        }
		    })
		}
		with open(data_path, "rb") as f:
			response: Response = post(f"{self.URL}/upload",
			                          f.read(),
			                          headers=header)
		print("Done!" if response.ok else f"Failed due to {response.reason}")
		return


def main(args):
	observer = SavefileManager("configs.json")
	if args.proc_name:
		observer.restore(args.proc_name)
	else:
		observer.watch()


if __name__ == "__main__":
	parser = argparse.ArgumentParser()
	parser.add_argument("-p",
	                    "--proc_name",
	                    help="The name of the process for restoration")

	args = parser.parse_args()
	main(args)
