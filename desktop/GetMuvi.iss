#define MyAppName "GetMuvi"
#define MyAppVersion "0.1.1"
#define MyAppPublisher "Carlos Vale Jr / GetMuvi"
#define MyAppExeName "GetMuvi.exe"

[Setup]
AppId={{A713CD2A-58CA-4E68-9AD6-849D74E89C8B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/carvalejr-22/YouTube-Downloader
AppSupportURL=https://github.com/carvalejr-22/YouTube-Downloader/issues
AppUpdatesURL=https://github.com/carvalejr-22/YouTube-Downloader/releases
DefaultDirName={autopf}\GetMuvi
DefaultGroupName=GetMuvi
DisableProgramGroupPage=yes
OutputDir=installer-output
OutputBaseFilename=GetMuvi-Setup-0.1.1
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=assets\getmuvi.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion=0.1.1.0
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription=Instalador oficial do GetMuvi
VersionInfoCopyright=Copyright (C) 2026 Carlos Vale Jr
VersionInfoProductName=GetMuvi
VersionInfoProductVersion=0.1.1.0

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "dist\GetMuvi.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\GetMuvi"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\GetMuvi"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Criar atalho na área de trabalho"; GroupDescription: "Atalhos adicionais:"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Abrir GetMuvi"; Flags: nowait postinstall skipifsilent
