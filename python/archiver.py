import io
import os
from multiprocessing import Pool
from zipfile import ZIP_DEFLATED, ZipFile


def archive_to_memory(path: str) -> bytes:
	'''
	Returns bytes, which is an zipped archive.
	To avoid GIL, multiprocessing.Pool is used.
	'''
	buffer = io.BytesIO()
	with Pool() as pool:
		return pool.starmap(archive, [(buffer, path)])[0]


def unpack_from_memory(buffer: io.BytesIO, dst):
	with Pool() as pool:
		pool.starmap(unpack, [(buffer, dst)])


def archive(buffer: io.BytesIO, path: str):
	with ZipFile(buffer, "w", ZIP_DEFLATED) as zipf:
		for root, dirs, files in os.walk(path):
			archive_root = os.path.relpath(root, path)
			for file in files:
				path_in_archive = os.path.join(archive_root, file)
				zipf.write(os.path.join(root, file), path_in_archive)

	return buffer.getvalue()


def unpack(archive_path, dst):
	with ZipFile(archive_path) as archive:
		archive.extractall(dst)
