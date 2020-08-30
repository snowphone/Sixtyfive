#!python
import argparse
import json
import logging
import os
from shutil import copy, make_archive, rmtree
from typing import List
from zipfile import ZipFile

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

		self.configs: List[dict] = configs["applications"]
		self.names: List[str] = [conf["name"] for conf in self.configs]
		return

	def _download(self, name: str):
		header = {
		    **self.BASE_HEADER, 
			"Dropbox-API-Arg": json.dumps({
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

		archive_name = proc_name[:-4] + ".zip"
		print(f"Downloading data of {proc_name}...")
		data = self._download(f"data/{archive_name}")

		with open(archive_name, "wb") as f:
			f.write(data)

		restore_path = config["save_path"]
		self._unpack_archive(archive_name, restore_path)

		os.remove(archive_name)
		print(f"Restoration complete!")
		return

	def _unpack_archive(self, archive_path, dst):
		archive = ZipFile(archive_path)
		archive.extractall(dst)
		archive.close()

	def _register_process(self, proc: Process):
		wait_procs([proc], callback=self._backup_savefile)
		return

	def _backup_savefile(self, proc: Process):
		proc_name = proc.info["name"]
		self.names.append(proc_name)

		src_path: str = next(config["save_path"] for config in self.configs
		                     if config["name"] == proc_name)

		print(f"{proc_name} is terminated. Backup savefiles...")
		make_archive(proc_name[:-4], "zip", src_path)

		archive_path = proc_name[:-4] + ".zip"
		self._upload(archive_path, proc_name)
		os.remove(archive_path)
		return

	def _upload(self, data_path: str, proc_name: str):
		header = {
		    **self.BASE_HEADER, 
			"Dropbox-API-Arg": json.dumps({
		        "path": f"/data/{proc_name[:-4]}.zip",
		        "mode": {
		            ".tag": "overwrite"
		        }
		    })
		}
		with open(data_path, "rb") as f:
			response: Response = post(f"{self.URL}/upload",
			                          f.read(),
			                          headers=header)
		print("Done!" if response.ok else f"Failed due to {response.content}")
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
