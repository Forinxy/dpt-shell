package com.luoye.dpt.builder;

import com.android.apksig.ApkSigner;
import com.luoye.dpt.config.ShellConfig;
import com.luoye.dpt.util.FileUtils;
import com.luoye.dpt.util.KeyUtils;
import com.luoye.dpt.util.LogUtils;
import com.luoye.dpt.res.ApkManifestEditor;
import com.luoye.dpt.util.ZipUtils;
import com.wind.meditor.core.FileProcesser;
import com.wind.meditor.property.AttributeItem;
import com.wind.meditor.property.ModificationProperty;
import com.wind.meditor.utils.NodeValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Apk extends AndroidPackage {

    public static class Builder extends AndroidPackage.Builder {
        @Override
        public Apk build() {
            return new Apk(this);
        }
    }

    protected Apk(Builder builder) {
        super(builder);
    }

    @Override
    protected File getOutAssetsDir(String packageDir) {
        return FileUtils.getDir(packageDir,"assets");
    }

    @Override
    public String getLibDir(String packageDir) {
        return packageDir + File.separator + "lib";
    }

    @Override
    public String getDexDir(String packageDir) {
        return packageDir;
    }

    @Override
    protected String getManifestFilePath(String packageOutDir) {
        return packageOutDir + File.separator + "AndroidManifest.xml";
    }

    @Override
    protected boolean sign(String packagePath, String keyStorePath, String signedPackagePath, String keyAlias, String storePassword, String KeyPassword) {
        try {
            KeyStore keyStore = KeyStore.getInstance(getKeyStoreType());
            try (FileInputStream fis = new FileInputStream(keyStorePath)) {
                char[] storePass = storePassword == null ? new char[0] : storePassword.toCharArray();
                keyStore.load(fis, storePass);
            }
            char[] keyPass = KeyPassword == null ? new char[0] : KeyPassword.toCharArray();
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keyPass);
            if (privateKey == null) {
                LogUtils.error("Failed to load signing key, alias not found: %s", keyAlias);
                return false;
            }
            java.security.cert.Certificate[] certChain = keyStore.getCertificateChain(keyAlias);
            if (certChain == null || certChain.length == 0) {
                LogUtils.error("Failed to load certificate chain for alias: %s", keyAlias);
                return false;
            }
            List<X509Certificate> certificates = new ArrayList<>(certChain.length);
            for (java.security.cert.Certificate certificate : certChain) {
                certificates.add((X509Certificate) certificate);
            }

            ApkSigner.SignerConfig signerConfig =
                    new ApkSigner.SignerConfig.Builder("CERT", privateKey, certificates).build();

            ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                    .setInputApk(new File(packagePath))
                    .setOutputApk(new File(signedPackagePath))
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(true)
                    .build();
            apkSigner.sign();
            return true;
        } catch (Exception e) {
            LogUtils.error("Failed to sign APK: %s", e);
            return false;
        }
    }

    @Override
    public void writeProxyAppName(String manifestDir) {
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeApplicationName(inManifestPath,outManifestPath, getProxyApplicationName());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void writeProxyComponentFactoryName(String manifestDir){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeAppComponentFactory(inManifestPath,outManifestPath, getProxyComponentFactory());

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setExtractNativeLibs(String manifestDir){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ModificationProperty property = new ModificationProperty();

        property.addApplicationAttribute(new AttributeItem(NodeValue.Application.EXTRACTNATIVELIBS, "true"));

        FileProcesser.processManifestFile(inManifestPath, outManifestPath, property);

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void setDebuggable(String manifestDir, boolean debuggable){
        String inManifestPath = manifestDir + File.separator + "AndroidManifest.xml";
        String outManifestPath = manifestDir + File.separator + "AndroidManifest_new.xml";
        ApkManifestEditor.writeDebuggable(inManifestPath,outManifestPath, debuggable ? "true" : "false");

        File inManifestFile = new File(inManifestPath);
        File outManifestFile = new File(outManifestPath);

        inManifestFile.delete();

        outManifestFile.renameTo(inManifestFile);
    }

    @Override
    public void saveApplicationName(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        ShellConfig shellConfig = ShellConfig.getInstance();
        String appName = ApkManifestEditor.getApplicationName(androidManifestFile);

        appName = appName == null ? "" : appName;
        appName = appName.startsWith(".") ? appName.substring(1) : appName;

        shellConfig.setApplicationName(appName);
    }

    @Override
    public void saveAppComponentFactory(String packageOutDir) {
        String androidManifestFile = getManifestFilePath(packageOutDir);
        ShellConfig shellConfig = ShellConfig.getInstance();

        String acfName = ApkManifestEditor.getAppComponentFactory(androidManifestFile);

        acfName = acfName == null ? "" : acfName;

        shellConfig.setAppComponentFactoryName(acfName);
    }

    private static void process(Apk apk) {
        byte[] encKey = KeyUtils.generateKey();

        File apkFile = new File(apk.getFilePath());
        //apk extract path
        String apkMainProcessPath = apk.getWorkspaceDir().getAbsolutePath();

        LogUtils.info("Workspace path: " + apkMainProcessPath);

        ZipUtils.unZip(apk.getFilePath(),apkMainProcessPath);

        String packageName = ApkManifestEditor.getPackageName(apkMainProcessPath + File.separator + "AndroidManifest.xml");
        apk.setPackageName(packageName);

        /*======================================*
         * Process AndroidManifest.xml
         *======================================*/
        apk.saveApplicationName(apkMainProcessPath);
        apk.writeProxyAppName(apkMainProcessPath);
        if(apk.isAppComponentFactory()){
            apk.saveAppComponentFactory(apkMainProcessPath);
            apk.writeProxyComponentFactoryName(apkMainProcessPath);
        }
        if(apk.isDebuggable()) {
            LogUtils.info("Make apk debuggable.");
            apk.setDebuggable(apkMainProcessPath, true);
        }
        apk.setExtractNativeLibs(apkMainProcessPath);

        /*======================================*
         * Process .dex files
         *======================================*/
        String assetsPath = apk.getOutAssetsDir(apkMainProcessPath).getAbsolutePath();

        apk.extractDexCode(apkMainProcessPath, assetsPath);
        apk.addJunkCodeDex(apkMainProcessPath);
        apk.compressDexFiles(apkMainProcessPath);
        apk.deleteAllDexFiles(apkMainProcessPath);
        apk.combineDexZipWithShellDex(apkMainProcessPath);
        apk.addKeepDexes(apkMainProcessPath);
        FileUtils.deleteRecurse(apk.getKeepDexTempDir(apkMainProcessPath));

        /*======================================*
         * Process .so files
         *======================================*/
        apk.copyNativeLibs(apkMainProcessPath);

        apk.encryptSoFiles(apkMainProcessPath, encKey);

        /*======================================*
         * Build package
         *======================================*/
        apk.writeConfig(apkMainProcessPath, encKey);

        apk.buildPackage(apkFile.getAbsolutePath(), apkMainProcessPath, FileUtils.getUserDir());

        File apkMainProcessFile = new File(apkMainProcessPath);
        if (apkMainProcessFile.exists()) {
            FileUtils.deleteRecurse(apkMainProcessFile);
        }
        LogUtils.info("All done.");
    }

    @Override
    public void protect() throws IOException {
        super.protect();
        process(this);
    }

}
