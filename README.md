# Phantom Mic

An LSPosed (Xposed/Edxposed) module to simulate microphone input 🎤 from a pre-recorded audio file, useful for **automating audio calls** 

## Tested Apps

| Application        | Status        |
| ------------------ | ------------- |
| Facebook Messenger | ✔ Working     |
| Discord            | ✔ Working     |
| Telegram           | ✔ Working     |
| Whatsapp           | ❌ Not working |
| .. You tell me!    |               |

Read **Developer Notes** for not working apps

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

- [ ] native hook

In the apps I tested, I found AudioRecord was used for most of them for streaming audio. As for the native hook (which would make phantom mic work on almost ANY app), I had a solution working on my device, but it's very tedious to bring compatibility to all android versions as the internal APIs on which my solution relies change frequently see [AudioRecord.cpp](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/av/media/libaudioclient/AudioRecord.cpp;l=1?q=AudioRecord.cpp&sq=&ss=android%2Fplatform%2Fsuperproject%2Fmain),

If you want to give it a shot, clone the branch native_hook and edit main.cpp (also return true in MainHook.isNativeHook)
