#!python.exe
import json
import logging
import os
from shutil import copy, make_archive, rmtree
import tempfile
from typing import List

from psutil import Process, process_iter, wait_procs
from requests import Response, post


class SavefileManager:
	TOKEN = "m9BGWnHvSw0AAAAAAAAAAdBGNzWtnFBRbPz7yknRC9anv9IKMjU7rEdw5ifv3k7V"
	URL = "https://content.dropboxapi.com/2/files/upload"

	def __init__(self, configs_path):
		with open(configs_path, "rt") as f:
			configs: dict = json.load(f)

		self.backup_root: str = configs["backup_location"]
		self.configs: List[dict] = configs["applications"]
		self.names: List[str] = [conf["name"] for conf in self.configs]
		return

	def listen(self):
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
		    "Authorization": f"Bearer {self.TOKEN}",
		    "Content-Type": "application/octet-stream",
		    "Dropbox-API-Arg": json.dumps({
		        "path": f"data/{proc_name[:-4]}.zip",
		        "mode": {
		            ".tag": "overwrite"
		        }
		    })
		}
		with open(data_path, "rb") as f:
			resp: Response = post(self.URL, f.read(), headers=header)
		print("Done!" if resp.ok else f"Failed due to {resp.reason}")
		return


def main():
	obj = SavefileManager("configs.json")
	obj.listen()


if __name__ == "__main__":
	main()
