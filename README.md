# Phone to Wear OS notification app
A app for mirroring phone notifications and media. Primarily made for secondary or "unsupported by the watch" phones.
It has features like media control,volume adjust with haptic,album art, notification replies,icons,auto reconnect, app filtering and delete-sync on both sides.Also has a test notification field, can be used for sending custom notifications. The app doesn't even have an icon yet, i don't know if it needs since it's designed to be set and forget type of app... help appericated!

# How does it work?
The phone app listens for new notification entries,If an app posts a notification, the app grabs the information/icon into compressed Base64 data. No google services are used, pure RFCOMM. No  data is being sent to servers (neither i know how to!), That means it's offline. 

# Installation (Phone side)
- Install the .apk file (May need to enable installing from "unknown sources".)
- Give the Neccessary permissions shown at the start (Be sure to disable battery optimization on settings)
- Select your device from the dropdown, It must be paired for it to show up.
- It should connect and start doing it's job when it's connected!

# Installation (Watch side)
- Install the .apk (I recommend AnExplorer to install .apk and File browser by Orienlabs to import the file)
- Give permission shown at first launch
- That's all!

