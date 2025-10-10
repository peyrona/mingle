# What is Mingle?

* A programming language (very close to natural language) named "_Une_".<br>
* The tools associated with the _Une_ language.

_Une_ is focused in the Internet of the Things ([IoT](https://en.wikipedia.org/wiki/Internet_of_things)) and it is extremely easy to learn: it is not needed to have programming skills.

After being compiled, _Une_ can be executed on most of the existing Operative Systems: Windows, MacOS, Linux, Solaris and others.


# How looks it like?

Here is how to say using _Une_ : <br>
“When the alarm is on and any door or window is opened, then send to my Telegram the message 'DANGER! Intruders at home'”.

![Une language basic example](une-1st-example.png)


# Questions and Answers

 **Do I need to know about computer programming?**

> No, you do not: as far as you are familiar with spreadsheets (e.g.: MS Excel) you can successfully use _Une_.
> It is also true that the more you know about programming computers and about electronics, the more you will be able to do.

 **What computer do I need?**

> A PC with Windows, macOS or Linux. In fact Mingle runs in any machine where Java 11 can run.
> Raspberry Pi is a 50 US$ computer very appropriate to experiment with _Une_.

 **How to install Mingle?**
> 1. Download lastest release (at the right side of this web page: onli the "mingle-*.zip" file is needed).
> 2. Create a folder in you PC named "Mingle" (or whatever you like).
> 3. Unzip the downlaoded relase file inside your local "Mingle" folder.
> 4. Either:
>    a) Install Java 11 or higher in your local machine.
>    b) Make a folder inside your 'Mingle' folder and make sure the name contains the word "jdk" or "jre" (e.g. "jdk.11.linux") and place inside the Java folder all Java needed files. You can download them from here: [Adoptium](https://adoptium.net)


 **What is the easiest way to start?**

> "Glue" is the name for the IDE (Integrated Development Environment), it has all you need: an editor, a compiler and an Execution Environment (to execute the compiled code). Using Glue you can even "see" what is hapenning inside the Execution Environment (ExEn).
>
> So, our recommendation is:
>
> 1.  Start Glue: for Linux, use "menu.sh"; for other OOSS use their "run_*" file.
> 2.  Open de editor (click the pencil icon at toolbar).
> 3.  Open an example (under 'examples' folder): they are numbered from easiest to smartest.
> 4.  Take "The Une language.pdf" handbook (under "docs" folder).
> 5.  Read, understand and test.
> 6.  Modify the _Une_ source code and re-test.
> 7.  Go to #3

 **What about MSP?**

> The "Mingle Standard Platform" is a set of tools created to make easy the daily tasks about _Une_ applications: create, compile, deploy and monitorize.
> You have a handbook (under "todeploy/docs" folder) titled "The Mingle Standard Platform.pdf". It is highly recommended to read it carefuly in order to be familiar with the tools you will daily use.
>
> These are the tools that compose the ISP:
>
> *   **Tape**: the transpiler. Used to "compile" _Une_ source scripts into a "model", which can be used by Stick.
> *   **Stick**: the Execution Environment. Where the "model"s run.
> *   **Glue**: an Intergated Development Environment that assist in the whole development/debugging/monitoring cycle.
> *   **Gum**: a HTML/Javscript based tool to monitorize one or more ExEns.
> *   **Updater**: Keeps MSP up to date.

 **What licence has Mingle?**

> Everything is Open Source (available under the Apache License 2.0): the language itself, the MSP specifications, all the source code and even the documentation (2 books created under the Creative Commons license): one about Mingle and the other about _Une_. So, everyone can use and distribute it for free (no charges, never, for nothing).

 **What about the logo?**

> The mascot for the _Une_ language is an electric anguilla named _Tune_. You can use it anywhere, as long as it's related to _Mingle_ or _Une_.