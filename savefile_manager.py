#!python.exe
import json
import logging
import os
from shutil import copy, make_archive
from typing import List

from psutil import Process, process_iter, wait_procs


class SavefileManager:
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
				self.register_process(proc)
			except StopIteration:
				continue

	def register_process(self, proc: Process):
		wait_procs([proc], callback=self.backup_savefile)
		return

	def backup_savefile(self, proc: Process):
		proc_name = proc.info["name"]
		self.names.append(proc_name)

		src_path = next(config["save_path"] for config in self.configs
		                if config["name"] == proc_name)
		dst_path = os.path.join(self.backup_root, proc_name[:-4])

		print(f"{proc_name} is terminated. Backup savefiles")
		make_archive(dst_path, "zip", src_path)
		return


def main():
	obj = SavefileManager("configs.json")
	obj.listen()


if __name__ == "__main__":
	main()
