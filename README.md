# Phantom Mic

An LSPosed (Xposed/Edxposed) module to simulate microphone input 🎤 from a pre-recorded audio file, useful for **automating audio calls** 

<details>
  <summary>Demo Video</summary>

https://github.com/user-attachments/assets/12a9d229-fd8a-4370-b969-1a342360abdf

</details>

## Tested Apps

| Application        | Status    |
| ------------------ | --------- |
| Facebook Messenger | ✔ Working |
| Discord            | ✔ Working |
| Telegram           | ✔ Working |
| Whatsapp **        | ✔ Working |
| Google Chrome      | ✔ Working |
| .. You tell me!    |           |

App ** : Recordings folder defaults to /sdcard/Android/data/app_id/files/Recordings because chose folder dialog doesn't work

Note: your app might work if it's not on the list, let us know if you tried it!

## Usage Guide

- Enable the module for your target app

- Open your target app, you'll be prompted to choose a folder it for the module files

- Inside the chosen folder, copy all your audio files to be played (*.mp3, *.wav, etc) 

- Inside the chosen folder create a new file phantom.txt, write the name of your target audio file (without extension is also fine) and save it. The file can be left empty if you want to use your microphone as normal.
  
  **<u>Bonus Tip:</u>** use an app like *MacroDroid* or *Tasker* to automate the process

<details>

<summary>Example</summary>

### Folder Structure

```
CHOSEN_FOLDER
|_ music.mp3
|_ whatevername.wav
|_ sample.aac
|_ phantom.txt
```

### Inside phantom.txt

```
music.mp3
```

</details>

## Requirements

- Android 7+

- [Root] LSPosed / Edxposed

- [No Root] LSPatch theoretically works but not tested

## Developer Notes

The app relies on native hooking [AudioRecord.cpp](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/av/media/libaudioclient/AudioRecord.cpp;l=1?q=AudioRecord.cpp&sq=&ss=android%2Fplatform%2Fsuperproject%2Fmain), feel free to take a look at the source code!

# Links

[XDA Thread](https://xdaforums.com/t/mod-xposed-phantom-mic-simulate-microphone-input-from-audio-file.4682767/#post-89623099)
