import os
import sys
import webbrowser as wb
from logging import Handler
from threading import Thread
from multiprocessing import freeze_support

import PyQt5.QtCore as core
import PyQt5.QtWidgets as wid
from PyQt5.QtCore import QThread
from PyQt5.QtGui import QCloseEvent, QIcon
from PyQt5.QtWidgets import (QApplication, QComboBox, QHBoxLayout, QPushButton,
                             QVBoxLayout, QWidget)
from requests import post

import sixtyfive

DEBUG = False


class WindowedApp(QWidget):
	class GUILogger(Handler, core.QObject):
		appendPlainText = core.pyqtSignal(str)

		def __init__(self, text_edit: wid.QTextEdit):
			super().__init__()
			core.QObject.__init__(self)

			self.widget = text_edit
			self.widget.setReadOnly(True)

			self.appendPlainText.connect(self.widget.append)
			return

		def emit(self, record):
			msg = self.format(record)
			self.appendPlainText.emit(msg)

	def __init__(self):
		super().__init__()

		self.setWindowTitle("Sixtyfive")
		self.setWindowIcon(QIcon("resources/icon.ico"))

		self.grid = wid.QGridLayout()

		sync_box = QVBoxLayout()
		sync_box.addStretch(1)
		self.cb = QComboBox()
		self.create_sync_layout(sync_box)
		sync_box.addStretch(1)
		self.line_proc_name = wid.QLineEdit()
		self.line_path = wid.QLineEdit()
		self.create_add_layout(sync_box)
		sync_box.addStretch(1)
		self.grid.addLayout(sync_box, 0, 0, core.Qt.AlignJustify)

		self.status = wid.QTextEdit()
		status_box = self.create_status_layout()
		self.grid.addLayout(status_box, 0, 1, core.Qt.AlignJustify)

		self.setLayout(self.grid)

		self.show()

		if not self.token_exists():
			token = self.authorize()
			self.store_token(token)

		if DEBUG:
			self.cb.addItems(["alice.exe", "bob.exe", "charlie.exe"])
		else:
			sixtyfive.log.addHandler(self.GUILogger(self.status))
			self.sixtyfive = sixtyfive.Sixtyfive()
			self.cb.addItems(self.sixtyfive.names)

		self.run()
		return

	def run(self):
		class Watcher(QThread):
			def __init__(self: core.QObject, parent, inst: sixtyfive.Sixtyfive):
				super().__init__(parent)
				self.inst = inst

			def run(self):
				self.inst.watch()

		self.watch_thread = Watcher(self, self.sixtyfive)
		self.watch_thread.start()

	@staticmethod
	def token_exists(path=sixtyfive.Sixtyfive.TOKEN_PATH) -> bool:
		return os.path.exists(path)

	@staticmethod
	def store_token(token: str, path=sixtyfive.Sixtyfive.TOKEN_PATH):
		with open(path, "wt") as f:
			f.write(token)
		return

	def authorize(self) -> str:
		'''
		Get a token by accessing the web.
		'''
		app_key = "q9zdzeo9ybjr89q"
		app_secret = "fh7ter6p0mnga8u"
		auth_url = f"https://www.dropbox.com/oauth2/authorize?client_id={app_key}&response_type=code"

		# Authorize
		wb.open(auth_url)
		auth_code, ok = wid.QInputDialog.getText(
			self, "Sign in", "Enter code after authorization: ")
		auth_code = auth_code.strip()
		assert ok

		# Exchange from code to token
		token_url = "https://api.dropboxapi.com/oauth2/token"
		params = {
		    "code": auth_code,
		    "grant_type": "authorization_code",
		    "client_id": app_key,
		    "client_secret": app_secret,
		}

		resp = post(token_url, data=params)
		resp.raise_for_status()

		return resp.json()["access_token"]

	def create_status_layout(self):
		self.status.setReadOnly(True)
		box = wid.QHBoxLayout()
		box.addWidget(self.status)
		return box

	def create_sync_layout(self, parent: wid.QBoxLayout):
		parent.addWidget(self.cb)
		self.cb.currentTextChanged.connect(self.show_on_change)

		hbox = QHBoxLayout()

		for btn in [
		    self.create_download_button(),
		    self.create_upload_button(),
		    self.create_show_path_button(),
		    self.create_remove_button()
		]:
			hbox.addWidget(btn)

		parent.addLayout(hbox)
		return

	def show_on_change(self):
		self.line_proc_name.setText(self.cb.currentText())
		self.line_path.setText(next(c['save_path'] for c in self.sixtyfive.configs if c['name'] == self.cb.currentText()))

	def create_show_path_button(self):
		btn = QPushButton("ShowButton", self)
		btn.setText("Expanded path")
		btn.clicked.connect(self.show_path_on_click)

		return btn

	def show_path_on_click(self):
		proc_name = self.cb.currentText()
		if DEBUG:
			print(f"Path of {proc_name} will be printed")
		else:
			self.sixtyfive.show_expanded_path(proc_name)

	def create_add_layout(self, parent: wid.QBoxLayout):
		hbox = QHBoxLayout()
		hbox.addWidget(wid.QLabel("Process name"))
		hbox.addWidget(self.line_proc_name)
		parent.addLayout(hbox)

		hbox = QHBoxLayout()
		hbox.addWidget(wid.QLabel("Path"))
		hbox.addWidget(self.line_path)
		parent.addLayout(hbox)

		parent.addWidget(self.create_add_button())

	def create_download_button(self):
		btn = QPushButton("DownloadButton", self)
		btn.setText("Download")
		btn.clicked.connect(self.download_on_click)

		return btn

	def download_on_click(self):
		proc_name = self.cb.currentText()
		if DEBUG:
			print(f"Download {proc_name}")
		else:
			Thread(target=self.sixtyfive.restore, args=[proc_name]).start()

	def create_upload_button(self):
		btn = QPushButton("UploadButton", self)
		btn.setText("Upload")
		btn.clicked.connect(self.upload_on_click)

		return btn

	def upload_on_click(self):
		proc_name = self.cb.currentText()
		if DEBUG:
			print(f"Upload {proc_name}")
		else:
			thread = Thread(target=self.sixtyfive.backup, args=[proc_name])
			thread.start()
		return

	def create_add_button(self):
		btn = QPushButton(self)
		btn.setText("Add / Modify")
		btn.clicked.connect(self.add_config_on_click)

		return btn

	def add_config_on_click(self):
		path = self.line_path.text().strip()
		proc_name = self.line_proc_name.text().strip()

		if DEBUG:
			print(f"Add path: {path}, name: {proc_name}")
		else:
			self.sixtyfive.add_config(proc_name, path)
			self.update_combo_box()

	def update_combo_box(self):
		self.cb.currentTextChanged.disconnect(self.show_on_change)
		self.cb.clear()
		self.cb.currentTextChanged.connect(self.show_on_change)
		self.cb.addItems(self.sixtyfive.names)

	def create_remove_button(self):
		btn = QPushButton(self)
		btn.setText("Remove")
		btn.clicked.connect(self.remove_config_on_click)
		return btn

	def remove_config_on_click(self):
		proc_name = self.cb.currentText()
		msg = wid.QMessageBox()
		response = msg.warning(self, "Warning!",
		                       f" Do you really want to delete {proc_name}? ",
		                       wid.QMessageBox.Yes, wid.QMessageBox.No)

		if response == wid.QMessageBox.Yes:
			self.sixtyfive.remove_config(proc_name)
			self.update_combo_box()


if __name__ == "__main__":
	freeze_support()
	app = QApplication(sys.argv)
	ex = WindowedApp()
	sys.exit(app.exec())
