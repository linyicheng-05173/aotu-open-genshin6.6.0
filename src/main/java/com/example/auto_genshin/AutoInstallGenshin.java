package com.example.auto_genshin;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

public class AutoInstallGenshin implements ClientModInitializer {
    // 国服（米哈游）
    private static final String YS_WEBSITE_CN = "https://ys.mihoyo.com/";
    private static final String YS_DOWNLOAD_CN = "https://ys-api.mihoyo.com/event/download_porter/link/ys_cn/official/pc_backup319";
    // 国际服（HoYoverse）
    private static final String YS_WEBSITE_GL = "https://genshin.hoyoverse.com/";
    private static final String YS_DOWNLOAD_GL = "https://sg-public-api.hoyoverse.com/event/download_porter/link/hoyoverse/pc_default";

    // ─── 三个版本的配置：名称、exe名、注册表路径、安装文件夹名 ───
    private static final String[][] GENSHIN_VERSIONS = {
        // { 显示名, exe文件名, 注册表key, 安装目录名, 注册表值名 }
        { "\u56fd\u670d\u539f\u795e", "YuanShen.exe",
          "HKLM\\SOFTWARE\\WOW6432Node\\miHoYo\\原神", "Genshin Impact",
          "InstallPath" },
        { "\u56fd\u9645\u670d\u539f\u795e", "GenshinImpact.exe",
          "HKLM\\SOFTWARE\\WOW6432Node\\miHoYo\\Genshin Impact", "Genshin Impact",
          "InstallPath" },
        { "\u4e91\u00b7\u539f\u795e", "YuanShen.exe",
          "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\云原神",
          "Genshin Impact Cloud Game",
          "InstallLocation" },
    };

    // 递归扫盘时跳过这些巨慢的目录
    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
        "Windows", "ProgramData", "$Recycle.Bin", "System Volume Information",
        "Recovery", "PerfLogs", "Windows.old", "Program Files", "Program Files (x86)",
        "MSOCache", "Config.Msi", "node_modules", ".gradle", ".m2", ".cache"
    ));

    private static final List<String> DRIVES = Arrays.asList("C:/", "D:/", "E:/", "F:/");

    @Override
    public void onInitializeClient() {
        // 统一转小写，避免 options.languageCode 返回 "zh_CN" 等大小写不一致导致匹配失败
        final String langCode = getLangCodeSafe().toLowerCase();
        final boolean isChinese = "zh_cn".equals(langCode);

        new Thread(() -> {
            try {
                    String[] found = findAnyGenshin(isChinese);
                    if (found != null) {
                        String name = found[0];
                        String path = found[1];
                        new ProcessBuilder("cmd", "/c", "start", "\"\"", path).start();
                        System.out.println("正在启动" + name + "：" + path);
                        // 原神启动成功 → 弹窗 + MC 闪退
                        Thread.sleep(2000);

                        String msgContent;
                        String msgTitle;
                        String crashReason;

                        switch (langCode) {
                            case "zh_cn":
                                msgTitle = "打开自动启动原神";
                                msgContent = "为确保原神运行流畅，已自动关闭正在运行中的Minecraft。";
                                crashReason = "为确保原神运行流畅，已自动关闭正在运行中的Minecraft。";
                                break;
                            case "zh_hk":
                                msgTitle = "打開自動啟動原神";
                                msgContent = "為確保原神運作順暢，已自動關閉正在執行的Minecraft。";
                                crashReason = "為確保原神運作順暢，已自動關閉正在執行的Minecraft。";
                                break;
                            case "zh_tw":
                                msgTitle = "打開自動啟動原神";
                                msgContent = "為確保原神執行流暢，已自動關閉執行中的Minecraft。";
                                crashReason = "為確保原神執行流暢，已自動關閉執行中的Minecraft。";
                                break;
                            case "ja_jp":
                                msgTitle = "原神を自動起動";
                                msgContent = "原神をスムーズに動作させるため、実行中のMinecraftを自動的に終了しました。";
                                crashReason = "原神をスムーズに動作させるため、実行中のMinecraftを自動的に終了しました。";
                                break;
                            case "ko_kr":
                                msgTitle = "원신 자동 실행";
                                msgContent = "원신이 원활하게 실행되도록 실행 중인 Minecraft를 자동으로 종료했습니다.";
                                crashReason = "원신이 원활하게 실행되도록 실행 중인 Minecraft를 자동으로 종료했습니다.";
                                break;
                            case "ru_ru":
                                msgTitle = "Автозапуск Genshin Impact";
                                msgContent = "Minecraft был автоматически закрыт для обеспечения плавной работы Genshin Impact.";
                                crashReason = "Minecraft был автоматически закрыт для обеспечения плавной работы Genshin Impact.";
                                break;
                            default:
                                msgTitle = "Auto Open Genshin Impact";
                                msgContent = "Minecraft has been closed to ensure Genshin Impact runs smoothly.";
                                crashReason = "Minecraft has been closed to ensure Genshin Impact runs smoothly.";
                                break;
                        }

                        // 用 PowerShell 弹系统级消息框，独立进程，不受 MC 崩溃影响
                        String psCmd = "Add-Type -AssemblyName System.Windows.Forms; "
                            + "[System.Windows.Forms.MessageBox]::Show('" + msgContent + "','" + msgTitle + "','OK','Information')";
                        new ProcessBuilder("powershell", "-Command", psCmd).start();
                        Thread.sleep(500);
                        // 弹窗结束后直接退出 MC
                        System.exit(0);
                    } else {
                        if (isChinese) {
                            System.out.println("检测到玩家未下载任何原神版本，正在打开国服官网并开始下载...");
                            openWebsite(YS_WEBSITE_CN);
                            Thread.sleep(1500);
                            openWebsite(YS_DOWNLOAD_CN);
                        } else {
                            System.out.println("检测到玩家未下载任何原神版本，正在打开国际服官网并开始下载...");
                            openWebsite(YS_WEBSITE_GL);
                            Thread.sleep(1500);
                            openWebsite(YS_DOWNLOAD_GL);
                        }
                    }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 安全获取 MC 语言设置。
     * 优先用 LanguageManager，未就绪时用 Options.languageCode，最后回退到英文。
     */
    private static String getLangCodeSafe() {
        try {
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            if (lang != null && !lang.isEmpty()) return lang;
        } catch (Exception ignored) {}
        try {
            // MC 26.1+ 不混淆，Options.languageCode 是 public 字段
            String lang = Minecraft.getInstance().options.languageCode;
            if (lang != null && !lang.isEmpty()) return lang;
        } catch (Exception ignored) {}
        // 最终回退：英文（而非中文），避免非中文用户看到中文弹窗
        return "en_us";
    }

    /**
     * 按优先级查找：中文 → 国服优先；非中文 → 国际服优先
     * @return [显示名, exe路径]，没找到返回 null
     */
    private String[] findAnyGenshin(boolean isChinese) {
        if (isChinese) {
            // 中文：国服 > 国际服 > 云
            for (int i = 0; i < GENSHIN_VERSIONS.length; i++) {
                String[] ver = GENSHIN_VERSIONS[i];
                String exePath = findVersion(ver[0], ver[1], ver[2], ver[3], ver[4]);
                if (exePath != null) return new String[] { ver[0], exePath };
            }
        } else {
            // 非中文：国际服 > 国服 > 云（国际服是索引1，国服是索引0）
            int[] order = {1, 0, 2};
            for (int idx : order) {
                String[] ver = GENSHIN_VERSIONS[idx];
                String exePath = findVersion(ver[0], ver[1], ver[2], ver[3], ver[4]);
                if (exePath != null) return new String[] { ver[0], exePath };
            }
        }
        return null;
    }

    /**
     * 查找某个版本：注册表 → 常见路径 → 递归扫盘
     */
    private String findVersion(String name, String exeName,
                               String regKey, String installDir, String regValue) {
        // 1. 注册表
        String regPath = readRegistry(regKey, regValue);
        if (regPath != null) {
            File exe = new File(regPath, installDir + " Game/" + exeName);
            if (exe.exists()) return exe.getAbsolutePath();
            // 云原神路径结构可能不同，也试一下直接在 installDir 下找
            exe = new File(regPath, exeName);
            if (exe.exists()) return exe.getAbsolutePath();
        }

        // 2. 常见安装路径
        List<String> folders = Arrays.asList(
            "Program Files/" + installDir,
            "Program Files (x86)/" + installDir,
            installDir
        );

        for (String drive : DRIVES) {
            for (String folder : folders) {
                File exe = new File(drive + folder, installDir + " Game/" + exeName);
                if (exe.exists()) return exe.getAbsolutePath();
                // 有些版本 exe 直接在根目录
                exe = new File(drive + folder, exeName);
                if (exe.exists()) return exe.getAbsolutePath();
            }
        }

        // 3. 递归扫盘
        for (String drive : DRIVES) {
            File root = new File(drive);
            if (!root.exists()) continue;
            String found = searchDir(root, 4, installDir + " Game", exeName);
            if (found != null) return found;
        }

        return null;
    }

    private String searchDir(File dir, int depth, String targetDir, String exeName) {
        if (depth <= 0) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                String fname = f.getName();
                if (SKIP_DIRS.contains(fname)) continue;
                if (fname.equalsIgnoreCase(targetDir)) {
                    File exe = new File(f, exeName);
                    if (exe.exists()) return exe.getAbsolutePath();
                }
                String found = searchDir(f, depth - 1, targetDir, exeName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String readRegistry(String regKey, String regValue) {
        try {
            Process p = new ProcessBuilder("reg", "query", regKey, "/v", regValue)
                    .redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(regValue)) {
                    String[] parts = line.trim().split("\\s+");
                    String path = parts[parts.length - 1];
                    if (new File(path).exists()) return path;
                }
            }
        } catch (Exception e) {
            // 注册表读不到不报错
        }
        return null;
    }

    private void openWebsite(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                new ProcessBuilder("cmd", "/c", "start", "\"\"", url).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
