# MonkeyRemote
Remote control your Android device via ADB

Warning: The screen updates are very slow (~1 frame per second).

## Build
    mvn package

## Usage
Run with:

    java -jar MonkeyRemote-0.5.jar "PATH TO ADB EXECUTABLE" SCALING_FACTOR PHONE_SERIAL_NUMBER

so for example:

    java -jar MonkeyRemote-0.5.jar "/usr/bin/adb" 0.5 RZ8R82G4F8Y

