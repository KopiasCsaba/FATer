# FATer
An ancient project from 2010, helping to cover/hide badblocks on flash disks.

<img src="https://raw.githubusercontent.com/KopiasCsaba/FATer/master/docs/fater.png">

See it in action here:
https://youtu.be/WmTN1H2x-vA


# How this stuff works?
This software is for testing and fixing (symptomatic treatment) flash drives with bad sectors. It fills the disk with small random files, and then it reads those back. If a file's content has not been damaged, the software removes that file. If a file is corrupted, then it will be kept there, and therefore that block of flash memory will be blocked from being used for useful content. 

This way you can use your mp3 player again without hearing glitches in your files in theory. 
In practice: who knows?:) 

But don't trust any drives with your important data that has badsectors, neither do it after you ran this application on it. 
Use this on your own risk only.  

This app operates in three phases:
 * File creation
 * Waiting for you to remount your drive (detach, unplug, replug...)
 * Reading the files back and removing uncorrupted files, leaving corrupted ones
