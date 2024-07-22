# Phantom Mic

An LSPosed (Xposed/Edxposed) module to simulate microphone input 🎤 from a pre-recorded audio file, useful for **automating audio calls** 

## Tested Apps

| Application        | Status    |
| ------------------ | --------- |
| Facebook Messenger | ✔ Working |
| Discord            | ✔ Working |
| Telegram           | ✔ Working |
| .. You tell me!    |           |

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

The following hooks where implemented

- [x] android.media.AudioRecord

- [ ] android.media.MediaRecorder

- [ ] libOpenSLES.so

In the apps I tested, I only found AudioRecord was used for streaming audio. For MediaRecorder, I intentionally didn't hook it because it can only save audio to files (not stream audio like in calls). As for libOpenSLES.so, it might also be used by some apps, but I haven't found any yet. Please open an issue if your target app uses one of these APIs.
