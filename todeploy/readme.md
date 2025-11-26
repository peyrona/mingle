### **README.md**

````markdown
# Mingle Application Launcher

This application comes with automated start scripts for Windows, macOS, and Linux. These scripts enable a "plug-and-play" experience by automatically handling the Java Runtime Environment (JRE) setup for the user.

## 🚀 How It Works

The included scripts (`run-win.ps1`, `run-mac.sh`, `run-lin.sh`) perform the following steps automatically:

1.  **Detect Java:** Checks if a valid Java installation exists in the following priority:
    * **Local Bundle:** Looks for a `jdk` or `jre` folder inside the application directory.
    * **JAVA_HOME:** Checks system environment variables.
    * **System PATH:** Checks if `java` is globally installed.
2.  **Auto-Install (If Missing):** If no Java installation is found, the script detects the system architecture (x64, x86, ARM64) and downloads **Adoptium JDK 11**.
3.  **Launch:** Starts the application (`menu.jar`) passing all provided arguments. If no argument provided and grahics hardware is detected, Glue (Mingle IDE) is launched.

---

## 📂 Installation & Setup

Ensure your application directory looks like this before running the scripts:

```text
/My-Application-Folder
  ├── menu.jar           # The main application file
  ├── run-win.ps1        # Windows Start Script
  ├── run-mac.sh         # macOS Start Script
  ├── run-lin.sh         # Linux Start Script
  └── README.md          # This documentation
````

-----

## 🖥️ Windows Users

**Prerequisites:** PowerShell 5.1 or newer (Standard on Windows 10/11).

1.  Navigate to the application folder.
2.  Right-click **`run-win.ps1`** and select **"Run with PowerShell"**.
3.  *First-run only:* If prompted to change the execution policy, type `Y` or `A` to allow the script to run.

**Alternative (Command Line):**

```powershell
.\run-win.ps1
```

-----

## 🍎 macOS Users

1.  Open the **Terminal** app.
2.  Navigate to the folder containing the application:
    ```bash
    cd /path/to/folder
    ```
3.  Grant execution permissions (required only once):
    ```bash
    chmod +x run-mac.sh
    ```
4.  Run the application:
    ```bash
    ./run-mac.sh
    ```

*Note: The script automatically detects Apple Silicon (M1/M2/M3) vs. Intel chips and downloads the correct architecture.*

-----

## 🐧 Linux Users

1.  Open your terminal.
2.  Navigate to the application folder.
3.  Grant execution permissions (required only once):
    ```bash
    chmod +x run-lin.sh
    ```
4.  Run the application:
    ```bash
    ./run-lin.sh
    ```

*Note: The script supports x64, x86, ARM64, and ARM32 architectures.*

-----

## 🔧 Troubleshooting

| Issue | Solution |
| :--- | :--- |
| **Permission Denied (Mac/Linux)** | Ensure you ran `chmod +x <script_name>` before executing. |
| **"menu.jar not found"** | The script must be in the same folder as `menu.jar`. Do not move the script to the Desktop by itself. |
| **Script closes immediately** | Run the script via a Terminal/PowerShell window to see the error logs. |
| **Download Fails** | Check your internet connection. The script attempts to download JDK 11 from `api.adoptium.net`. |

```