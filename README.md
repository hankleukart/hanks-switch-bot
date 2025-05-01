# Hank's Switch Bot Hubitat App

**Simple, Zero-Config Lighting Control Based on Device Names**

## Overview

Hank's Switch Bot is a Hubitat Elevation app designed to dramatically simplify the process of controlling lights and scenes from multi-button switches. Forget creating dozens of rules or manually linking devices to buttons. This app automatically figures out the relationships between your switches, lights, and scenes based primarily on their device names and room properties.

You simply select all the switches, lights, and scenes you want the app to manage, and Switch Bot builds the control logic for you. It’s designed to work perfectly with CoCoHue and Inovelli switches but can be used with other brands of switches and lights.

## Features

- **Automatic Name-Based Mapping:** Controls lights and scenes based on logical relationships derived from device names and the `roomName` property.
- **Zone Support:** Devices with zone tags (`[Zone Name]` in their device name or label) can be controlled together via switches having the same zone tag using a double-tap.
- **Scene Mode:** A dedicated mode (toggleable via a configurable button) allows quick cycling through matched scenes for a switch.
- **Sibling Switch Sync:** If you have multiple switches for controlling the same lights, the app sync their lights and switch state.
- **Mode Lighting Defaults:** Configure default levels and color temperatures for different Hubitat modes, applied when lights are turned on by the app.
- **Customizable Button Mappings:** Default button assignments (like which button triggers "Lights On" or "Start Dim Up") can be adjusted in preferences.

## Device Naming Guide

Switch Bot analyzes the display names of the devices you select to build its internal maps. Devices must be named as follows:

- **Name Stem:** The app looks for lights or scenes whose display name starts with the stem of the switch name before the word “Switch”.
	- Example: `Living Room Overhead Switch` -> Controls `Living Room Overhead Lights`, `Living Room Overhead Fan`.
- **Master and All Switches:** A switch named just the room name followed by "Switch" (e.g. `Kitchen Switch`) will control all other lights/scenes with matching stems that are *not* already controlled by a more specific switch in the same room. A switch with the word All after the room name (e.g. `Kitchen All Switch`) automatically controls all lights with the Kitchen stem.
- **Zone Tag:** Including a zone name in square brackets in the device's *name* or *label* field (e.g. `Hallway Switch [Living Zone]`) associates that switch (and lights/scenes with the same zone tag) with that zone. Zone tags are used across-room zone on/off actions (double tap up/down).

## Device Naming Example

Here is an example of how you might set up rooms with device names:

- Kitchen All Switch
	- Kitchen Ceiling
	- Kitchen Toaster
	- Kitchen Island Switch
		- Kitchen Island 1
		- Kitchen Island 2
	- Kitchen Scene 1: Cooking
	- Kitchen Scene 2: Eating
	- Kitchen Scene 3: Party
- Living Room Switch
	- Living Room Ceiling [Living Area]
	- Living Room Lamp [Living Area]
- Dining Room Switch
	- Dining Room Scene 1: Normal [Living Area]
	- Dining Room Scene 2: Party [Living Area]

## Usage (Default Button Mappings)

By default, Hank's Switch Bot uses these mappings pre-defined for Inovelli switches (configurable in Advanced settings):

- **Tap Up:**
	- **Normal Mode:** Lights on and set to *current* mode's brightness level and color temperature. If already in that state, Tap Up sets to the *default* mode's brightness level. For scene-only switches (switches mapped to scenes but no lights), Tap Up cycles to the next scene, even outside scene mode.
	- **Scene Mode:** Cycle forward to the next scene.
- **Tap Down:**
	- **Normal Mode:** Lights off. For scene-only switches, turns off any lights with matching *room* stem, or otherwise, turns off *zone* lights.
	- **Scene Mode:** Cycle backward to the previous scene.
- **Tap Up 2x:** Zone or room lights on and set to *current* mode's level and CT.
- **Tap Down 2x:** Zone or room lights off.
- **Hold Up:** Dim up.
- **Hold Down:** Dim down.
- **Config Button:** Toggle Scene Mode on/off for that switch. On Inovelli switches, the LED bar will chase to indicate Scene Mode. Scene Mode automatically exits after a period of inactivity (default 7 seconds, configurable).

## Installation

1. Tap the Apps code section in your Hubitat Elevation web interface.
2. Click **"+ Add App"**.
3. Click the three-dot menu and then **"Import.”** 
4. Paste the direct URL to the raw Groovy code for Hank's Switch Bot from this repository: https://raw.githubusercontent.com/hankleukart/hanks-switch-bot/main/hanks-switch-bot.groovy
5. Click **"Import"**, then **"Save"**.

## Configuration

1. Go to the Apps section in your Hubitat web interface.
2. Click **"Add User App"**.
3. Select **"Hank's Switch Bot"** from the list.
4. In the app's main preferences:
	- Select **all** the switches you want this app to manage under "Switches controlled by Switch Bot".
	- Select **all** the lights and scenes you want this app to control under "Lights & Scenes controlled by Switch Bot". (Selecting all is recommended for comprehensive mapping).
5. Scroll down and click **"Done"**.
6. *Optional:* Review the "Switch Control Mappings Summary" section (appears after saving) to see how the app mapped your devices.
7. *Optional:* Configure "Mode Lighting Defaults" to set preferred brightness/CT for different modes.
8. *Optional:* Adjust "Advanced Button Mappings" if you want to change which button number/event triggers each action (Area On, Zone Off, Dimming, Scene Mode Toggle, etc.).
9. Click **"Done"** again after making any optional changes.

The app will now subscribe to button events on your selected switches and control the mapped devices.

## Support and Issues

Please report any issues or ask questions in the official Hubitat forum thread:
https://community.hubitat.com/t/hanks-switch-bot-zero-config-lighting-control-based-on-device-names-inovelli-hue/153029

## License

This app is licensed under the Apache License, Version 2.0. See the license text at the top of the Groovy code file for details.

---

*Copyright 2025 Hank Leukart*
