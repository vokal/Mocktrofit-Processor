package io.vokal.gradle.mocktrofit

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import groovy.json.*

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.*;

import javax.inject.Inject

class MocktrofitPlugin implements Plugin<Project> {

    Project project;

    void apply(Project project) {

        this.project = project
        def hasApp = project.hasProperty('android')
        def root = project.getProjectDir().getAbsolutePath();
        def fs = FileSystems.getDefault();

        def processed = []

        project.extensions.create("mocktrofit", MocktrofitPlugin)
        project.afterEvaluate {
            def variants = project.android.hasProperty('applicationVariants') ?
                project.android.applicationVariants : project.android.libraryVariants;
            variants += project.android.testVariants

            variants.each { variant ->
                def tname = "rename${variant.getName().capitalize()}Mocks"
                def task = project.tasks.create(name: tname) << { 
                    Path srcPath =  fs.getPath(variant.mergeAssets.getOutputDir().toString())
                    moveAssets(fs, srcPath, processed)
                }
                variant.getMergeAssets().finalizedBy task 
            }
        }
    }

    private void moveAssets(FileSystem fs, Path path, processed) {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                moveFile(fs, file, path.toString(), processed);
                return FileVisitResult.CONTINUE;
            }

            @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc == null) {
                        return FileVisitResult.CONTINUE;
                    } else {
                        throw exc;
                    }
                }
            });
    }

    private static void moveFile(FileSystem fs, Path file, String root, List processed) {
        if (file.getFileName().toString().endsWith(".http")) {
            
            String name = file.getFileName().toString().replaceAll("\\.http", "")
            Path newPath = fs.getPath(file.getParent().toString(), encrypt(name) + ".http")

            String key = fs.getPath(file.getParent().toString(), name + ".http").toString();
            key = key.replaceAll(root, "");

            if (!processed.contains(key)) {
                Files.move(file, newPath)
                processed.add(newPath.toString().replaceAll(root, ""));
            }
        }
    }

    private static String encrypt(String name) {
        String sha1 = name;
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(name.getBytes("UTF-8"));
            sha1 = new BigInteger(1, crypt.digest()).toString(16);
        } catch(NoSuchAlgorithmException|UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return sha1;
    }
}

class MocktrofitExtension {
    String mockFolder = "mocks"
}
