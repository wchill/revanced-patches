This is a fork of https://github.com/ReVanced/revanced-patches that includes patches (https://github.com/ReVanced/revanced-patches/pull/4551) to help Boost evade detection by Reddit.

# Instructions
Instructions on usage (from https://www.reddit.com/r/revancedapp/comments/1j4s7bd/how_to_fix_the_403_error_for_boost_using_revanced/):

1. Back up your settings in Boost and then uninstall it.

2. Grab a fresh copy of the Boost APK from [here](https://www.apkmirror.com/apk/ruben-mayayo/boost-for-reddit/boost-for-reddit-1-12-12-release/boost-for-reddit-1-12-12-android-apk-download/). Don't install it.

3. Change your ReVanced Manager settings to point to my patches as shown [here](https://github.com/user-attachments/assets/0094627f-fb5e-45fd-97f3-6ee24d21027e)

4. Create an installed app [here](https://www.reddit.com/prefs/apps/). For redirect uri, set it to `http://127.0.0.1:8080`. You can leave about url blank.

5. Open the Boost APK in ReVanced Manager and make sure the Spoof client patch is enabled. For OAuth client ID, copy the random looking text from https://www.reddit.com/prefs/apps (below "Installed app"). Leave Redirect URI alone. For User agent, set it to something like `android:com.yourusername.reddit:v1.0 (by /u/yourusername)` (replace with your username).

6. Patch and install, your Boost should work now. You can restore your settings using the backup you made earlier.

See https://github.com/ReVanced/revanced-patches/issues/4549#issuecomment-2702966919 for more details.

# FAQ

I get "Error: Invalid request to Oauth API"

* Your redirect URI is probably wrong. It has to match exactly between the patching settings and your reddit installed app. If you're reusing an installed app you made before, then update the URI at https://www.reddit.com/prefs/apps/. Make sure you don't add `/` at the end.

I still get 403 Blocked

* User agent needs to follow the format above. If you fill it in with garbage, it will eventually be blocked by reddit. Also, reddit blocks any mention of rubenmayayo in the user agent.

I'm getting 400 Bad Request while logged in

* Try logging out and logging back in if you didn't uninstall the app in step 1

I can't check the box for Spoof client; I get `Notice: This patch contains a required option that is not supported by this app`

* Go to settings and toggle "Version compatibility check". I don't know why this fixes it

I get a `null: null` error when I open the app

* Your client ID is incorrect, check that you copied it correctly

Can I skip any steps if I was using the moderator workaround

* No, you have to follow all of the above steps
