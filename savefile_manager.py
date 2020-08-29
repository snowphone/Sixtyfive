#!python.exe
import json
import logging
import os
from shutil import copy, make_archive
from typing import List

from psutil import Process, process_iter, wait_procs

STORE_PATH = r"C:\Users\mjo97\Downloads\backup"

configs = [{
    "name":
    "splintercell3.exe",
    "save_path":
    r"C:\ProgramData\Ubisoft\Tom Clancy's Splinter Cell Chaos Theory",
}, {
    "name": "SpaceSniffer.exe",
    "save_path": r"C:\Users\mjo97\Downloads\temp"
}]


class SavefileManager:
	def __init__(self, configs: List[dict]):
		self.configs = configs
		self.names = [config["name"] for config in configs]
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
				print(f"Found a process '{name}'")
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
		dst_path = os.path.join(STORE_PATH, proc_name[:-4])

		print(f"{proc} is just terminated. Backup savefiles")
		make_archive(dst_path, "zip", src_path)
		return


def main():
	obj = SavefileManager(configs)
	obj.listen()


if __name__ == "__main__":
	main()
