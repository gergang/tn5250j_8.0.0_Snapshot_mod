This modified tn5250j is based on version 8.0.0 Snapshot @ https://github.com/tn5250j/tn5250j/

tn5250j08mod.zip contains the runnable jar file.

This modification was done a tn5250j version was needed to better interact with a calling program.
Changes were done to the following files:

ConnectDialog
 - Dialog & connections scrollpane are now resizable
 - Checkbox added to connections panel to overwrite encryption setting
 - If no sessions are active Cancel & Window closing will now properly shut down the JVM  
 
My5250
 - Code cleaned up
 - Shutdown logic changed to avoid buffer overflow error
 - Shutdown put into its own method & made public hence overwritable
 - New Action Event: FIND_AND_OPEN_EXISTING_SESSION

Small changes to  
- Gui5250Frame
- GlobalConfigure
- EmulatorActionEvent
- GUIViewInterface

to make certain elements accessible from outside.


# TN5250J
A 5250 terminal emulator for the IBM i (AS/400) written in Java.

Documentation is available at: [tn5250j.github.io](https://tn5250j.github.io/)

[![Build Status](https://travis-ci.org/tn5250j/tn5250j.svg?branch=travis)](https://travis-ci.org/tn5250j/tn5250j)

## History

This project was created because there was no terminal emulator for Linux with features like continued edit fields, gui windows, cursor progression fields, etc.

It was then open sourced to give something back to all those hackers and code churners that work so hard to provide the Linux and Open Source communities with quality work and software.



## Hosting

The project was previous hosted at [sourceforge.net](https://sourceforge.net/projects/tn5250j/). But since 2016 has been migrated to GitHub.
