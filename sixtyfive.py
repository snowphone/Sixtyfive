#!python
import argparse
import json
import logging
import os
from shutil import copy, make_archive, rmtree
from sys import stderr
from typing import List
from zipfile import ZipFile

from psutil import Process, process_iter, wait_procs
from requests import Response, post


class Sixtyfive:
	URL = "https://content.dropboxapi.com/2/files"


	def __init__(self, configs_name):
		self.token = self._read_token()
		self.header_base = {
			"Authorization": f"Bearer {self.token}",
			"Content-Type": "application/octet-stream",
			}

		self.full_configs = json.loads(self._download(configs_name))

		self.configs: List[dict] = self.full_configs["applications"]
		self.names: List[str] = [conf["name"] for conf in self.configs]

		return

	def _read_token(self, path="token.txt") -> str:
		with open(path, "rt") as f:
			return f.readline().strip()

	def _download(self, name: str):
		header = {
		    **self.header_base,
			"Dropbox-API-Arg": json.dumps({
		        "path": f"/{name}",
		    })
		}
		resp: Response = post(f"{self.URL}/download", headers=header)
		resp.raise_for_status()
		return resp.content

	def watch(self):
		log.info(f"Watching {self.names}...")
		while True:
			try:
				proc: Process = next(
				    proc for proc in process_iter(attrs=["pid", "name"])
				    if proc.info["name"] in self.names)
				name = proc.info["name"]

				self.names.remove(name)
				log.info(f"{name} started")
				self._register_process(proc)
			except StopIteration:
				continue

	def restore(self, proc_name):
		try:
			config: dict = next(conf for conf in self.configs
			                    if conf["name"] == proc_name)
		except StopIteration as e:
			log.error(f"{proc_name} does not exist in the configuration")
			raise

		archive_name = proc_name[:-4] + ".zip"
		log.info(f"Downloading data of {proc_name}...")
		data = self._download(f"data/{archive_name}")

		with open(archive_name, "wb") as f:
			f.write(data)

		restore_path = config["save_path"]
		self._unpack_archive(archive_name, restore_path)

		os.remove(archive_name)
		log.info(f"Restoration completed!")
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
		log.info(f"{proc_name} is terminated.")
		self.names.append(proc_name)
		self.backup(proc_name)

	def backup(self, proc_name: str):
		try:
			src_path: str = next(config["save_path"] for config in self.configs
		                     if config["name"] == proc_name)
		except StopIteration as e:
			log.error(f"{proc_name} does not exist in the configuration")
			raise

		make_archive(proc_name[:-4], "zip", src_path)
		log.info(f"Archived files in {src_path}")

		archive_path = proc_name[:-4] + ".zip"
		self._upload(archive_path, proc_name)
		os.remove(archive_path)
		return

	def _upload(self, data_path: str, proc_name: str):
		header = {
		    **self.header_base, 
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
		log.info("Uploaded successfully!" if response.ok else f"Failed due to {response.content}")
		return


def main(args):
	observer = Sixtyfive("configs.json")
	if args.download:
		observer.restore(args.download)
	elif args.upload:
		observer.backup(args.upload)
	elif args.list:
		txt = json.dumps(observer.full_configs, indent=True)
		print(txt)
	else:
		observer.watch()


if __name__ == "__main__":
	log = logging.getLogger("sixtyfive")
	log.setLevel(logging.INFO)
	log.addHandler(logging.StreamHandler(stderr))

	parser = argparse.ArgumentParser()
	parser.add_argument("-d",
	                    "--download",
	                    help="The name of the process for manual restoration")
	parser.add_argument("-u",
	                    "--upload",
	                    help="The name of the process for manual backup")
	parser.add_argument("-l", 
						"--list",
						help="Show stored configurations",
						action="store_true")

	args = parser.parse_args()
	main(args)
