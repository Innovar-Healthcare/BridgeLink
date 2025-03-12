# BridgeLink - an interop Open Source Community 

1. [Useful Links](#useful-links)
2. [General Information](#general-information)
3. [Installation and Upgrade](#installation-and-upgrade)
4. [Starting BridgeLink - an Open Source Fork of Mirth Connect](#starting-mirth-connect)
5. [Running BridgeLink - an Open Source Fork of Mirth Connect in Java 9 or greater](#java9)
6. [Java Licensing](#java-licensing)
7. [License](#license)

------------

<a name="useful-links"></a>
## 1. Useful Links
- [Downloads](https://github.com/nextgenhealthcare/connect/releases) 
- [User Guide](https://docs.nextgen.com/)
- [Wiki](https://github.com/nextgenhealthcare/connect/wiki)
  - [FAQ](https://github.com/nextgenhealthcare/connect/wiki/Frequently-Asked-Questions)
  - [What's New in Mirth Connect](https://github.com/nextgenhealthcare/connect/wiki/Release-Notes)
- [Forums](https://forums.mirthproject.io/)
- [Slack Channel](https://mirthconnect.slack.com/) 
  - [Slack Registration](https://join.slack.com/t/mirthconnect/shared_invite/zt-1prqon9tg-UQ_~6AsV8IwdITTo3z1aoA)

------------

<a name="general-information"></a>
## 2. General Information
##### About Innovar
Innovar helps make healthcare data move—securely, efficiently, and without the headaches. We help some of the biggest names in healthcare streamline their integrations, cut through complexity, and stay ahead in an industry that never stops evolving.
Our flagship product, Lightswitch, along with our deep expertise in Mirth Connect, gives organizations the tools they need to connect systems, manage data, and keep things running smoothly. Whether it’s a small practice, a large HIE, or a national network, we make sure our clients can focus on what they do best—delivering care—while we handle the data.
##### About OSS Mirth Connect
Like an interpreter who translates foreign languages into the one you understand, OSS Mirth Connect translates message standards into the one your system understands. Whenever a &quot;foreign&quot; system sends you a message, Mirth Connect&apos;s integration capabilities expedite the following:
- Filtering &mdash; Mirth Connect reads message parameters and passes the message to or stops it on its way to the transformation stage.
- Transformation &mdash; Mirth Connect converts the incoming message standard to another standard (e.g., HL7 to XML).
- Extraction &mdash; Mirth Connect can &quot;pull&quot; data from and &quot;push&quot; data to a database.
- Routing &mdash; Mirth Connect makes sure messages arrive at their assigned destinations.

Users manage and develop channels (message pathways) using the interface known as the Administrator:
![Administrator screenshot](https://i.imgur.com/tnoAENw.png)

------------

<a name="installation-and-upgrade"></a>
## 3. Installation and Upgrade
Mirth Connect installers are available for individual operating systems (.exe for Windows, .rpm and .sh for Linux, and .dmg for Mac OS X). Pre-packaged distributions are also available for individual operating systems (ZIP for Windows, tar.gz for Linux, and tar.gz for Mac OS X). The installer allows you to automatically upgrade previous Mirth Connect installations (starting with version 1.5).

Mirth Connect installers also come with the option to install and start a service that will run in the background. You also have the option of installing and running the Mirth Connect Server Manager, which allows you to start and stop the service on some operating systems, change Mirth Connect properties and backend database settings, and view the server logs.

An optional Mirth Connect Command Line Interface can be installed, allowing you to connect to a running Mirth Connect Server using a command line. This tool is useful for performing or scripting server tasks without opening the Mirth Connect Administrator.

The Mirth Connect Administrator Launcher can also be installed, allowing you to manage connections to multiple Mirth Connect servers and configure options such as Java runtime, max heap size, and security protocols.

After the installation, the Mirth Connect directory layout will look as follows:

- /appdata/mirthdb: The embedded database (Do NOT delete if you specify Derby as your database). This will be created when the Mirth Connect Server is started. The path for appdata is defined by the dir.appdata property in mirth.properties.
- /cli-lib: Libraries for the Mirth Connect Command Line Interface (if installed)
- /client-lib: Libraries for the Mirth Connect Administrator
- /conf: Configuration files
- /custom-lib: Place your custom user libraries here to be used by the default library resource.
- /docs: This document and a copy of the Mirth Connect license
- /docs/javadocs: Generated javadocs for the installed version of Mirth Connect. These documents are also available when the server is running at `http://[server address]:8080/javadocs/` (i.e. `http://localhost:8080/javadocs/`).
- /extensions: Libraries and meta data for Plug-ins and Connectors
- /logs: Default location for logs generated by Mirth Connect and its sub-components
- /manager-lib: Libraries for the Mirth Connect Server Manager (if installed)
- /public_html: Directory exposed by the embedded web server
- /server-launcher-lib: Libraries in this directory will be loaded into the main Mirth Connect Server thread context classloader upon startup. This is required if you are using any custom log4j appender libraries.
- /server-lib: Mirth Connect server libraries
- /webapps: Directory exposed by the embedded web server to host webapps

------------

<a name="starting-mirth-connect"></a>
## 4. Starting BridgeLink - an OSS Mirth Connect Fork
Once BridgeLink is installed, there are a few ways to launch the Mirth Connect Administrator. On Windows, you’ll find a shortcut in the Start Menu that launches it directly.

If that option isn’t available, you can access the BridgeLink Administrator launch page, which by default should be at http://[server address]:8080 (e.g., http://localhost:8080). The recommended method is using the Administrator Launcher, which you can download by clicking the Download Administrator Launcher button. When you click Launch BridgeLink Administrator, it will download a Java Web Start file for your server. Open that file with the Administrator Launcher to connect. The server listens on https://[server address]:8443 (e.g., https://localhost:8443).

For a fresh install, the default login credentials are admin/admin, but be sure to change them immediately for security reasons.

The first time you launch the administrator, it will load the necessary libraries. This lets you run the Administrator from any remote BridgeLink server without needing to install a separate client.

You may also see a security warning when launching (the exact dialog depends on your browser). This happens because BridgeLink generates a self-signed certificate by default. 

Click Run to proceed.

------------

<a name="java9"></a>
## 5. Running BridgeLink - an OSS Mirth Connect Fork -  in Java 9 or greater
In order to run Mirth Connect in Java 9 or greater, copy the options from `docs/mcservice-java9+.vmoptions` and append them to either mcserver.vmoptions or mcservice.vmoptions, depending on your deployment. Then restart Mirth Connect.

To run the Mirth Connect Command Line Interface, create a new file named mccommand.vmoptions in the Mirth Connect root directory. Copy all of the options from `docs/mcservice-java9+.vmoptions` into mccommand.vmoptions and save before launching the Command Line Interface.

------------

<a name="java-licensing"></a>
## 6. Java Licensing
In 2019, Oracle significantly changed licensing for official Oracle Java releases. You must now purchase a license in order to receive updates to the commercial version of Oracle Java. In response to this change, we officially added support for OpenJDK in Mirth Connect. OpenJDK receives free updates from Oracle for a period of 6 months following each release. While the Oracle OpenJDK distribution is recommended for use with Mirth Connect, we strive to support third-party OpenJDK distributions as well such as AdoptOpenJDK, Azul Zulu and Amazon Corretto. Third party distributions may receive extended release updates from their respective communities, but these are not guaranteed.

------------

<a name="license"></a>
## 7. License
BridgeLink is released under the [Mozilla Public License version 2.0](https://www.mozilla.org/en-US/MPL/2.0/ "Mozilla Public License version 2.0"). You can find a copy of the license in `server/docs/LICENSE.txt`.

All licensing information regarding third-party libraries is located in the `server/docs/thirdparty` folder.
