# Music Capsule

Dynamic Island-style music overlay for Android, designed for the centered camera cutout on Samsung Galaxy S23.

## Install using Termux and GitHub Actions

1. Extract the archive and enter the project directory.
2. Create an empty GitHub repository.
3. Push the project:

```bash
pkg install git -y
git init
git add .
git commit -m "Music Capsule v1"
git branch -M main
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

4. Open GitHub → Actions → **Build Music Capsule APK**.
5. Download the `Music-Capsule-debug-apk` artifact and install `app-debug.apk`.

## First launch

Grant both requested permissions in this order:

1. Display over other apps.
2. Notification access (needed by Android to expose active MediaSession controllers).

Start playback in any properly implemented Android media player. A pulsing dot appears beside the centred camera. Tap it to expand the capsule equally to both sides and downward. The capsule folds back two seconds after the last touch. When playback is paused, it disappears after five seconds.

## Notes

- Some Samsung firmware may require disabling battery optimization for Music Capsule if the listener is stopped in the background.
- Players that do not publish a MediaSession cannot be controlled by third-party apps.
- The debug APK is signed automatically by the Android debug key and is suitable for direct installation/testing.
