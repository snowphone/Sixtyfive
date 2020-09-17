pip install -r requirements.txt
pip install pyinstaller
pyinstaller --onefile --distpath . --clean --windowed app.py --add-data "resources/*;resources/" -i resources/icon.ico
