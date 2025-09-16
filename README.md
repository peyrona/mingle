# What is Mingle?

* A programming language (very close to natural language) named "_Une_".<br>
* The tools associated with the _Une_ language.

_Une_ is focused in the Internet of the Things ([IoT](https://en.wikipedia.org/wiki/Internet_of_things)) and it is extremely easy to learn: it is not needed to have programming skills.

After being compiled, _Une_ can be executed on most of the existing Operative Systems (Windows, MacOS, Linux, Solaris and others).
The full name is: "Mingle Standard Platform" ('MSP' or 'Mingle' for short).

Everything is Open Source (available under the Apache License 2.0): the language itself, the MSP specifications, all the source code and even the documentation (2 books created under the Creative Commons license).

# How looks it like?

Here is how to say using _Une_ : <br>
“when the alarm is on and any door or window is opened, then send the message "DANGER! Intruders at home" to my Telegram”.

![Une language basic example](une-1st-example.png)

# Questions and Answers

 **Do I need to know computers programming?**

> No, you do not: as far as you are familiar with spreadsheets (e.g.: MS Excel) you can successfully use _Une_.
> It is also true that the more you know about programming computers and about electronics, the more you will do.

 **How should I start?**

> A PC with Windows, macOS or Linux. In fact it runs in any machine where Java can run.
> Raspberry Pi is a 50 US$ computer very appropriate to experiment with _Une_, but you do not need it.

 **What is the easiest way to start?**

> "Glue" is the name for the IDE (Integrated Development Environment), it has all you need: an editor, a compiler and an Execution Environment (to execute the compiled code). Using Glue you can even "see" what is hapenning inside the Execution Environment.
>
> So, our recommendation is:
>
> 1.  Start Glue
> 2.  Open de editor (click icon at toolbar)
> 3.  Open an example (under 'examples' folder): they are numbered from easier to harder
> 4.  Take "The Une language.pdf" handbook (under "docs" folder)
> 5.  Read, understand and test
> 6.  Modify the _Une_ source code and re-test
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

 **What about Raspberry Pi?**

> We recommend to use it as your development environment: Mingle works out-of-the-box smoothly with RPi GPIO.
> And Glue (our IDE) allows to monitorize from a PC an _Une_ program running inside a RPi.
