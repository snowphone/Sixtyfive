#!/usr/bin/python3
import argparse
import enum
import json
import logging
import os
from shutil import copy, make_archive, rmtree
from sys import argv, stderr
from typing import Callable, List, Tuple, TypedDict
from zipfile import ZipFile

from psutil import Process, process_iter, wait_procs
from requests import Response, post


class ConfigType(TypedDict):
	name: str
	save_path: str


class ConfigsType(TypedDict):
	applications: List[ConfigType]

OptionType = Callable[["Sixtyfive", str], Tuple[dict, str]]

class Sixtyfive:
	CONFIG_NAME = "configs.json"
	URL = "https://content.dropboxapi.com/2/files"

	class PostHeaderOptions(enum.Enum):
		'''
		Each enum returns a function, returning a tuple of (headers, POST URL).
		'''
		DOWNLOAD: OptionType = lambda inst, path_name: ({
		    **inst.header_base,
			"Dropbox-API-Arg": json.dumps({
		        "path": f"/{path_name}",
		    })
		}, f"{inst.URL}/download")

		UPLOAD: OptionType = lambda inst, name: ({
		    **inst.header_base,
			"Dropbox-API-Arg": json.dumps({
		        "path": f"/{name}" if name == Sixtyfive.CONFIG_NAME else f"/data/{name[:-4]}.zip",
		        "mode": {
		            ".tag": "overwrite"
		        }
		    })
		}, f"{inst.URL}/upload")

	def __init__(self, configs_name=CONFIG_NAME):
		self.token = self._read_token()
		self.header_base = {
			"Authorization": f"Bearer {self.token}",
			"Content-Type": "application/octet-stream",
		}

		self.full_configs: ConfigsType = json.loads(self._download(configs_name))

		return

	@property
	def configs(self) -> List[ConfigType]:
		return self.full_configs["applications"]

	@property
	def names(self) -> List[str]:
		return [conf["name"] for conf in self.configs]

	@staticmethod
	def _read_token(path="resources/token.txt") -> str:
		with open(path, "rt") as f:
			return f.readline().strip()

	def _download(self, name: str):
		resp = self._post(self.PostHeaderOptions.DOWNLOAD, name)
		resp.raise_for_status()
		return resp.content

	def _post(self, post_option: OptionType, name: str, data=None) -> Response:
		'''
		Post a data to Dropbox. It provides upload and download.

		:param post_option: PostHeaderOptions.UPLOAD or PostHeaderOptions.DOWNLOAD
		:param name: It can be one of both: Sixtyfive.CONFIG_NAME, or proc_name
		:param data: it is used if and only if an UPLOAD enum is used.
		'''

		headers, url = post_option(self, name)
		if post_option == self.PostHeaderOptions.UPLOAD:
			return post(url, data, headers=headers)
		else:
			return post(url, headers=headers)

	def watch(self):
		log.info(f"Watching processes: {', '.join(self.names)}")
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
			config: ConfigType = next(conf for conf in self.configs
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

	def add_config(self, proc_name: str, save_path: str):

		# If a process already exists, remove the previous one.
		if legacy := next((it for it in self.configs
							if it["name"] == proc_name or it["save_path"] == save_path), None):
			log.info(f"The save path \'{legacy['save_path']}\' already exists. Replace the path with \'{save_path}\'.")
			self.configs.remove(legacy)

		new_config = {
			"name": proc_name,
			"save_path": save_path
		}
		self.configs.append(new_config)

		serialized = json.dumps(self.full_configs, indent=True)

		response = self._post(self.PostHeaderOptions.UPLOAD, self.CONFIG_NAME, serialized)
		response.raise_for_status()
		log.info(f"Successfully uploaded new configuration: {new_config}")
		print(serialized)
		return

	def remove_config(self, proc_name: str):

		# If a process already exists, remove the previous one.
		to_be_deleted = next((it for it in self.configs if it["name"] == proc_name), None)
		if not to_be_deleted:
			log.info(f"Process named {proc_name} does not exist")
			return

		self.configs.remove(to_be_deleted)

		serialized = json.dumps(self.full_configs, indent=True)

		response = self._post(self.PostHeaderOptions.UPLOAD, self.CONFIG_NAME, serialized)
		response.raise_for_status()
		log.info(f"{proc_name} is successfully removed")
		return

	def show_path(self, proc_name: str):
		config = next(c for c in self.configs if c['name'] == proc_name)
		log.info(f"The path of \'{proc_name}\' is \'{config['save_path']}\'")

	@staticmethod
	def _unpack_archive(archive_path, dst):
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
			src_path: str = next(config["save_path"] for config in self.configs if config["name"] == proc_name)
		except StopIteration as e:
			log.error(f"{proc_name} does not exist in the configuration")
			raise

		log.info(f"Archiving files in {src_path}")
		make_archive(proc_name[:-4], "zip", src_path)
		log.info(f"Archiving done! Now uploading the archive")

		archive_path = proc_name[:-4] + ".zip"
		self._upload_savefile(archive_path, proc_name)
		os.remove(archive_path)
		return

	def _upload_savefile(self, data_path: str, proc_name: str):
		with open(data_path, "rb") as f:
			response = self._post(self.PostHeaderOptions.UPLOAD, proc_name, f.read())
		log.info("Uploaded successfully!" if response.ok else f"Failed due to {str(response.content)}")
		return


def main(args):
	observer = Sixtyfive()
	if args.download:
		observer.restore(args.download)
	elif args.upload:
		observer.backup(args.upload)
	elif args.add_path and args.add_process:
		observer.add_config(args.add_process, args.add_path)
	elif args.list:
		txt = json.dumps(observer.full_configs, indent=True)
		print(txt)
	else:
		observer.watch()


log = logging.getLogger("sixtyfive")
log.setLevel(logging.INFO)
log.addHandler(logging.StreamHandler(stderr))

if __name__ == "__main__":
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
	parser.add_argument("--add_process",
						required="--add_path" in argv,
						help="A name of the process for a new configuration")
	parser.add_argument("--add_path",
						required="--add_process" in argv,
						help="A path of the process for a new configuration")

	args = parser.parse_args()
	main(args)
