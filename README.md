# Sixtyfive

This is a synchronization tool for applications which do not support configuration synchronization.
Once the registered application starts, Sixtyfive waits for the termination and then synchronize data that you specified.

For example, the command `Sixtyfive.exe --add 'WindowsTerminal.exe=%LOCALAPPDATA%/Packages/Microsoft.WindowsTerminal_8wekyb3d8bbwe/LocalState'` 
will let the program trace Windows Terminal and synchronize as soon as it terminates.
As shown in the example, Sixtyfive supports environment variables such as `LOCALAPPDATA`, `USERPROFILE`, `APPDATA`, and so on.
Moreover, `STEAM` variable is supported, pointing to `<STEAM>/steamapps/common`.

For more information, try this application with `--help` argument.


## Installation

`./gradlew[.bat] install` will create a script in `<root>/build/install/Sixtyfive/bin/`, but JDK must be installed.
Otherwise, `./gradlew[.bat] jpackage` will include JDK and package as an executable (.exe|elf) in `<root>/build/jpackage/`.
