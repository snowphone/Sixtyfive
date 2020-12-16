pip install -r requirements.txt
pip install pyinstaller
pyinstaller --distpath . --clean --windowed app.py --add-data "resources/*;resources/" -i resources/icon.ico
