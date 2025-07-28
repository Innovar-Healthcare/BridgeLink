## ⚠️ Version Notice: Commercial vs Community

This guide includes instructions for **both the Commercial and Community versions** of the HL7 Spec Extractor.

We’re committed to supporting the healthcare interoperability community by providing a **Community Version** that allows users to generate HL7 message specifications using the command-line tool.

For organizations that require a visual interface, streamlined workflows, and dedicated support, the **Commercial Edition** includes a **Web Interface** in addition to all command-line functionality.

> ⚠️ **If you're using the Community Version, you will not have access to the Web Interface.**  
> All interaction must be done via command line unless upgraded to the Commercial Edition.

To learn more or inquire about upgrading, visit [innovarhealthcare.com](https://www.innovarhealthcare.com).



# HL7 Specifications Extractor - User Guide

# Overview

The HL7 Spec Extractor is a lightweight utility designed to extract
structural specifications from HL7 message files. It parses HL7 v2.x
messages, identifies segments, fields, and components, and outputs the
extracted details into structured, developer-friendly formats such as
JSON or CSV.

This tool is ideal for interface analysts, integration engineers, and QA
teams who need accurate HL7 schema references for development or testing
without manual lookup.

# Key Benefits

- **Accelerates Development:** Quickly generates segment and field maps
  for HL7 messages.

- **Reduces Errors**: Eliminates manual parsing and ensures consistency.

- **Reusable Outputs:** Produces files that can be used in integration
  projects, documentation, and validation tools.

- **Lightweight and Simple:** No heavy installation required, runs as a
  simple command-line utility.

# Architecture Overview

The HL7 Spec Extractor follows a simple design:

- **Input Layer:** Accepts HL7 message files from a designated input
  directory.

- **Processing Layer:** Parses segments, fields, and components
  according to HL7 v2.x standards.

- **Output Layer:** Writes extracted specifications to JSON, CSV and PDF
  for easy consumption.

# Features and Capabilities

### Analysis Capabilities

- Field-level analysis: Data types, lengths, usage patterns, and
  frequency stats.

- Segment sequence profiling: Identify common segment patterns and
  variations.

- Multi-message type support: Separate reports for ADT, ORM, ORU, etc.

- PHI-aware processing: Automatically excludes sensitive fields from
  value analysis.

- Complex data type handling: Full parsing of XCN, CX, XAD, and other
  composite types.

### **Performance Optimizations**

- **Parallel Processing**: Multi-core parsing with configurable worker
  counts.

- **Streaming Mode**: Memory-efficient processing for large datasets.

- **S3 Integration**: Direct processing of HL7 files from AWS S3
  buckets.

- **Resume Capability**: Continue interrupted sessions without starting
  over.

- **Memory Optimization**: Batch processing for very large files

### **Output Formats**

- **JSON**: Machine-readable specifications.

- **HTML**: Interactive, searchable reports.

- **PDF**: Formal documentation for stakeholders.

- **Markdown**: Readable text reports.

- **CSV**: Exportable for spreadsheet analysis.

### Filtering Options

- Extract by message type (e.g., ADT, ORM, ORU).

- Include or exclude specific segments.

- Generate combined or individual message type reports.

### Validation and Error Detection

- Detects unknown segments or fields.

- Flags missing required fields.

- Provides usage statistics for all fields.

### Batch Processing

- Processes multiple HL7 files in a single execution.

- Handles large datasets efficiently with streaming and parallelization.

# System Requirements

**Hardware Requirements**

- **Memory**: 4 GB RAM minimum; 8 GB+ recommended for large datasets.

- **Storage**: Sufficient disk space for temporary files and generated
  reports (cleaned up automatically after processing).

- **Processor**: Multi-core CPU recommended for parallel processing.

**Software Requirements**

- **Operating System**: Linux, macOS, or Windows.

- **Python**: Version 3.8 or higher.

**Python Dependencies**

- **Core**

  - hl7 -- HL7 message parsing

  - tqdm -- Progress bars

  - multiprocessing -- Parallel processing (built-in)

  - pathlib -- Path handling (built-in)

- **Reporting**

  - markdown2 -- Markdown to HTML conversion

  - weasyprint -- PDF generation

  - jinja2 -- HTML and PDF template rendering

- **Optional (for AWS S3 integration)**

  - boto3 -- AWS S3 client

  - pandas -- CSV processing

**System Libraries for PDF Generation**

- **macOS**:\
  brew install cairo pango gdk-pixbuf libffi

- **Linux (Debian/Ubuntu)**:\
  sudo apt-get install libcairo2-dev libpango1.0-dev
  libgdk-pixbuf2.0-dev libffi-dev

- **Windows**:\
  Install GTK+ runtime or use Windows Subsystem for Linux (WSL).

# **Before You Begin**

1.  **Download the HL7 Spec Extractor Package**

> Obtain the ZIP installer from your provided ***Innovar distribution***
> source.

2.  **Extract the Package**

> Unzip the contents to a secure location.
>
> **Recommended Location**:
>
> C:\\Program Files\\HL7_Spec_Extractor\\
>
> (or another directory with read/write access).

3.  **Verify the License** (**IMPORTANT)**

> Ensure you have received a **valid license** from **Innovar**.
>
> Required files:
>
> license.json
>
> license_public_key.pem
>
> These files **must be in the same directory** as run_hl7_web.cmd
> before launching.
>
> If the license is missing or invalid, the application **will not
> start**.

4.  **Confirm Files Exist**

> *After extracting, the folder should contain:*
>
> ***run_hl7_web.cmd** (launcher script)*
>
> ***license.json** and **license_public_key.pem** (license files)*
>
> *Application binaries and configuration files*

# Launching the HL7 Spec Extractor

The easiest way to launch the HL7 Spec Extractor is by using the
included **Windows batch file (run_hl7_web.cmd)**. This script automates
the setup process so you don't have to manage Python environments or
dependencies manually.

**What the Launcher Does:**

When you run **run_hl7_web.cmd**, it:

- **Sets up the application environment** so all paths and
  configurations are correct.

- **Verifies Python dependencies** are available (no manual installs
  required).

- **Validates the license files** (license.json and
  license_public_key.pem) before launching.

- **Starts the HL7 Spec Extractor web application** on your local
  machine.

- **Displays the access URL** (usually http://localhost:8501) in the
  command window so you can open it in a browser.

- **Keeps the command window open** for status messages and
  troubleshooting.

**How to Launch**

You have two options:

1.  **From Command Line**

cd C:\\hl7_spec_extractor (Your installation path)

run_hl7_web.cmd

You'll see console messages for license validation and the server
starting.

2.  **By Double-Clicking** in Windows Explorer:

    - Navigate to the installation folder.

    - Double-click run_hl7_web.cmd.

    - This runs in a terminal window and launches the web UI.

![A screenshot of a computer AI-generated content may be
incorrect.](media/image3.png)

**Once you launch the script you might get a Windows protected your PC
dialog. Please click "More info" select run anyway.**

***Important:\
Only proceed if the script was provided by Innovar Healthcare.***

![A screenshot of a computer AI-generated content may be
incorrect.](media/image4.png) ![A screenshot of a computer AI-generated
content may be incorrect.](media/image5.png)

**Access the Web Interface**

Once started, the script displays:

Running on http://127.0.0.1:8501

Open this URL in your browser.

![A screenshot of a computer AI-generated content may be
incorrect.](media/image6.png)

**Need to run things manually or want more control?**\
See **Appendix A: Advanced Installation and Manual Setup** for details.

# Using the HL7 Spec Extractor Web Interface

The HL7 Specification Generator also provides a web-based interface for
users who prefer a graphical workflow instead of command-line
operations. The GUI simplifies the process by allowing you to configure
inputs, outputs, and processing options through an intuitive interface.

### Accessing the Web Interface

- Launch the application and navigate to:\
  [**http://localhost:8501/**](http://localhost:8501/) in your browser.

- Ensure the backend service is running before opening the interface.

### **Input Configuration**

- **HL7 Messages Folder Path**: Enter or browse to the folder containing
  HL7 message files.

- Make sure the directory contains .hl7 or text files with valid HL7
  messages.

- **Warning**: If the folder is empty or contains unsupported files, a
  message will appear:\
  *"No HL7 files found in this folder."*

### **Output Configuration**

- **Output Folder**: Specify the directory where reports will be saved.

- **Output Filename**: Enter a base name for your specification files
  (do not include extensions).

- Output files will include multiple formats (JSON, HTML, PDF, etc.).

### **Processing Options**

- **Use Parallel Processing**: Enable multi-core processing for faster
  execution.

- **Max Workers**: Set the number of workers using the slider (default:
  4).

- **Combined Reports Only**: Select this option to skip individual
  message type reports and generate a single combined specification
  file.

- **Tip**: For large datasets, consider enabling streaming mode via CLI
  (not available in the GUI yet).

![A screenshot of a computer AI-generated content may be
incorrect.](media/image7.png)

### Running the Specification Generation

1.  Confirm that the **input folder** and **output folder** paths are
    correct.

2.  Select your desired options for processing.

3.  Click **Generate Specification**.

    - The tool will process all HL7 files in the input folder and
      generate reports in the specified output directory.

4.  Once complete, click **Open Output Folder** to view your results.

![A screenshot of a computer AI-generated content may be
incorrect.]

# HL7 File Output Structure

After processing, the HL7 Specification Generator creates a set of files
in the specified output folder. These files provide different formats
for easy review, sharing, and automation.

**Key Advantages of Multi-Format Output**

- **JSON**: Ideal for developers and automation scripts.

- **Markdown**: Lightweight format for internal documentation and Git
  repositories.

- **PDF**: Shareable and formal format for compliance and distribution.

- **HTML**: Interactive viewing experience with search and filtering.

# Understanding the Generated Reports

After processing HL7 messages, the HL7 Spec Extractor generates multiple
report formats for different use cases. This section provides an
overview of what each format contains and the key features available.

## Interactive HTML Report

- **Purpose**\
  Provides a visual, user-friendly way to explore HL7 message
  specifications in an interactive dashboard.

- **Includes**

  - **Summary Statistics**: Total messages processed, number of unique
    message types.

  - **Message Type Distribution**: Counts and percentages by type (e.g.,
    ADT\^A01, ORU\^R01).

  - **Segment Presence Summary**: Frequency of each segment across all
    messages.

  - **Segment Sequences**: Common segment patterns by message type.

  - **Combined Field Specifications**: Field-level details including
    names, HL7 types, lengths, usage stats, and sample values.

- **Key Features**

  - Search and filter across segments and fields.

  - Toggle between light and dark modes.

  - Export data to CSV or other formats.

  - Organized tabs for easy navigation (Overview, Segments, Sequences).

![A screenshot of a computer AI-generated content may be
incorrect.](media/image9.png){width="3.334808617672791in"
height="3.8933891076115485in"}

### Markdown Report (.md)

- **Purpose**\
  A lightweight, text-based report for developers and technical teams
  who prefer easy-to-read documentation that can be version-controlled.

- **Includes**

  - Message type distribution with counts and percentages.

  - Segment sequence tables grouped by message type.

  - Segment presence summary.

  - Combined field specification tables for each segment.

- **Key Features**

  - Simple, text-based format ideal for GitHub or internal
    documentation.

  - Can be rendered nicely in Markdown viewers for readability.

> ![A screenshot of a computer AI-generated content may be
> incorrect.](media/image10.png){width="4.562145669291339in"
> height="3.189278215223097in"}

### JSON Specification (.json)

- **Purpose**\
  A machine-readable format for integration with analytics tools, custom
  dashboards, or automated validation workflows.

- **Includes**

  - Complete structural definition of all segments and fields.

  - Data types, presence counts, min/max lengths.

  - Value distributions and usage statistics.

- **Key Features**

  - Ideal for programmatic use and automation.

  - Can be parsed for custom analytics or conformance checking.

- **Recommended Screenshot**

  - JSON snippet showing a segment (e.g., PID) with its fields and usage
    data.

![A screenshot of a computer program AI-generated content may be
incorrect.](media/image11.png)

### PDF Report

- **Purpose**\
  A formal, print-ready report for documentation and distribution to
  compliance or business stakeholders.

- **Includes**

  - Executive summary of processed messages.

  - Message type distribution tables.

  - Segment sequence patterns for each message type.

  - Segment presence summary across all messages.

  - Combined field specification tables with:

    - HL7 field names, descriptions, data types, usage stats, and
      example values.

  - Value reference tables for frequently used fields (e.g., message
    types, attending doctors, observation codes).

- **Key Features**

  - Professional, static format suitable for audits or internal reviews.

  - Well-structured tables for easy navigation.

  - Ready for printing or secure sharing.

![A screenshot of a report AI-generated content may be
incorrect.](media/image12.png){width="4.94509186351706in"
height="3.820541338582677in"}

# Combined vs. Message-Level Reports

The HL7 Spec Extractor generates **two categories of reports**:\
**Combined Reports** for an entire dataset, and **Message-Level
Reports** for individual HL7 message types.

### Combined Reports

- **Scope**: All HL7 messages processed, across all message types.

- **Purpose**: Ideal for:

  - High-level documentation.

  - Compliance reviews.

  - Overall understanding of field usage across the system.

- **Content Highlights**:

  - Summary statistics for all messages.

  - Global segment presence and frequency.

  - Combined field specifications.

### Message-Level Reports

- **Scope**: Single HL7 message type (e.g., ADT_A01, ORU_R01).

- **Purpose**: Ideal for:

  - Developers creating or validating specific interfaces.

  - QA teams validating field presence and value distributions.

- **Content Highlights**:

  - Detailed field specifications for that message type.

  - Value distribution tables (e.g., gender breakdown, location usage).

  - Common segment patterns for that message.

  -----------------------------------------------------------------------
  **Task**                      **Recommended Report**
  ----------------------------- -----------------------------------------
  **Document overall HL7        **Combined Report**
  structure**                   

  **Analyze usage across        **Combined Report**
  message types**               

  **Build or test a specific    **Message-Level Report**
  interface**                   

  **QA on a single message      **Message-Level Report**
  type**                        
  -----------------------------------------------------------------------

# Running HL7 Spec Extractor via CLI (Command Line)

This will summarize how to run the extractor using **local file
processing** and **S3 batch processing**, including examples and key
options, in the same professional tone as your existing guide.

### Command-Line Usage

The HL7 Spec Extractor can be executed directly from the command line,
providing flexibility for automation and large-scale processing. This
section outlines common commands and options.

### Basic Local Processing

To process HL7 messages stored locally and generate specifications:

HL7_SPEC_EXTRACTOR.EXE ***C:\\Path\\To\\HL7\\InputFolder
C:\\Path\\To\\Output\\ReportFolder***

- First Path: Path to the folder containing HL7 files.

- Second Path: Output file name for the generated specification.

### Advanced Local Processing

Enable advanced features such as parallel processing or streaming:

Multi-core processing with 4 workers

HL7_SPEC_EXTRACTOR.EXE . C:\\Path\\Input C:\\Path\\Output \--parallel
\--workers 4

Streaming mode for large datasets

HL7_SPEC_EXTRACTOR.EXE C:\\Path\\Input C:\\Path\\Output \--streaming
\--batch-size 500

Combined report only

HL7_SPEC_EXTRACTOR.EXE C:\\Path\\Input C:\\Path\\Output \--combined-only

### Command-Line Options

  -----------------------------------------------------------------------------
  **Option**         **Description**
  ------------------ ----------------------------------------------------------
  \--parallel        Enables multi-core processing (recommended).

  \--workers N       Sets number of parallel workers (default: auto).

  \--streaming       Enables memory-efficient processing.

  \--batch-size N    Sets batch size in streaming mode (default: 1000).

  \--combined-only   Skips individual message-type reports.
  -----------------------------------------------------------------------------

# Appendix A: Advanced Installation and Manual Troubleshooting

This guide explains how to install and configure the HL7 Spec Extractor
on **Windows**.\
While examples are for Windows, we include macOS and Linux references
for completeness.

## Prerequisites

- **Python**: Version 3.8 or higher\
  Download: https://www.python.org/downloads/\
  During installation, check **"Add Python to PATH"**.

- **PIP**: Comes with Python (verify below).

- **Admin rights**: Required to edit system PATH or install system
  dependencies.

- **Optional**: Visual Studio Build Tools for compiling some Python
  packages.

**Verify Python and pip**:

python \--version

pip --version

![A screen shot of a computer AI-generated content may be
incorrect.](media/image13.png)

## Create a Virtual Environment 

Using a virtual environment isolates dependencies for this tool.

Open **Command Prompt**:

Navigate to your project directory

cd C:\\hl7_spec_extractor

# Create virtual environment

python -m venv hl7_env

# Activate environment

hl7_env\\Scripts\\activate

You should see (hl7_env) before your prompt after activation.

![A screen shot of a computer AI-generated content may be
incorrect.](media/image14.png){width="6.575569772528434in"
height="2.400207786526684in"}

## Install **Python Dependencies**

You have two options:

### Option A: Install from requirements.txt (Recommended)

pip install -r requirements.txt

**What this does:**

- Installs the **latest available version** of each package listed in
  requirements.txt from [PyPI](https://pypi.org/).

- If the package is **already installed**, pip checks:

  - If it's the latest version, it does **nothing**.

  - If it's older than the latest, pip **upgrades it to the newest
    version**.

**Important Note:**\
If you are concerned about version consistency or want to **keep a
pre-existing version of a package**, do **not** use this method.
Instead, proceed to **Option B** and install packages individually to
maintain control over versions.

![A screen shot of a computer program AI-generated content may be
incorrect.](media/image15.png)

**Packages Needed:**

nginx

CopyEdit

hl7

tqdm

cryptography

streamlit

markdown2

weasyprint

boto3

pandas

### Option B: Install Individually

# Core HL7 processing

pip install hl7 \# HL7 message parsing and handling

pip install tqdm \# Progress bars for processing large batches

# Security and streaming

pip install cryptography \# Secure data handling and encryption

pip install streamlit \# Lightweight UI for interactive workflows

# Report generation

pip install markdown2 \# Converts markdown reports to HTML

pip install weasyprint \# Generates professional PDF reports

# S3 integration (optional)

pip install boto3 \# AWS SDK for accessing S3 buckets

pip install pandas \# Data analysis and CSV export support

### Install System Dependencies for PDF Reports (GTK+ Runtime)

The HL7 Spec Extractor uses **WeasyPrint** for PDF generation, which
requires GTK and related system libraries. Without these, PDF exports
will fail.

**Windows Installation**

1.  **Download GTK+ Runtime**\
    Get the latest GTK installer for Windows:\
    [[https://www.gtk.org/download/windows.php]{.underline}](https://www.gtk.org/download/windows.php)

2.  **Install GTK Runtime**

    - Run the installer.

    - Use the default installation path or choose a custom directory
      (e.g., C:\\GTK).

3.  **Add GTK to System PATH**

    - Add the **bin** folder from the GTK installation to your system
      PATH:

> C:\\GTK\\bin

- **Steps**:

  - Open **System Properties → Advanced → Environment Variables**.

  - Under **System Variables**, edit **Path** and add:\
    C:\\GTK\\bin

- Click **OK** and restart Command Prompt.

4.  **Verify GTK Installation**\
    In Command Prompt, run:

> where pango-view
>
> If this returns a path under C:\\GTK\\bin, GTK is installed correctly.

***macOS (for reference)***

*brew install cairo pango gdk-pixbuf libffi*

***Linux (for reference)***

*sudo apt-get install libcairo2-dev libpango1.0-dev libgdk-pixbuf2.0-dev
libffi-dev*

### Verify Python Installation

Check that Python and pip work:

python \--version

pip list

**Ensure required packages appear in the list.**

### **If Windows Hijacks Python (Troubleshooting)**

**If Windows Hijacks Python, Turn This Off**

Windows can override your Python installation using **App Execution
Aliases**, which redirects python and python3 commands to the Microsoft
Store instead of your actual Python installation. This often causes
python and pip not to work, even if you installed them correctly.

**Fix the Issue:**

1.  Open **Settings → Apps → Advanced App Settings → App Execution
    Aliases**.

2.  Scroll to find:

python.exe

python3.exe

3.  Turn both **OFF**.

**Why This Matters:**\
Disabling these aliases ensures that your system uses the Python
installation you configured, not the Microsoft Store placeholder.

![A screenshot of a
computer](media/image16.png)
