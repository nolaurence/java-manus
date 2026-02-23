package cn.nolaurene.cms.service.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.worker.resp.file.*;
import cn.nolaurene.cms.exception.manus.AppException;
import cn.nolaurene.cms.exception.manus.BadRequestException;
import cn.nolaurene.cms.exception.manus.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: File Operation Service
 */
@Slf4j
@Service
public class FileService {

    @Async("fileTaskExecutor")
    public CompletableFuture<FileReadResult> readFile(String file, Integer startLine, Integer endLine, boolean sudo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!sudo && !Files.exists(Paths.get(file))) {
                    throw new ResourceNotFoundException("File not found: " + file);
                }

                String content;
                if (sudo) {
                    ProcessBuilder pb = new ProcessBuilder("sudo", "cat", file);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        content = sb.toString();
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new BadRequestException("Failed to read file with sudo");
                    }
                } else {
                    content = new String(Files.readAllBytes(Paths.get(file)));
                }

                // 处理行范围
                if (startLine != null || endLine != null) {
                    String[] lines = content.split("\n");
                    int start = (startLine != null) ? startLine : 0;
                    int end = (endLine != null) ? endLine : lines.length;

                    content = String.join("\n", Arrays.asList(lines).subList(start, end));
                }

                return new FileReadResult(content, file);
            } catch (Exception e) {
                if (e instanceof ResourceNotFoundException || e instanceof BadRequestException) {
                    throw new CompletionException(e);
                }
                throw new CompletionException(new AppException("Failed to read file: " + e.getMessage()));
            }
        });
    }

    @Async("fileTaskExecutor")
    public CompletableFuture<FileWriteResult> writeFile(String file, String fileContent,
                                                        boolean append, boolean leadingNewline,
                                                        boolean trailingNewline, boolean sudo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String contentToWrite = fileContent;
                // 处理换行符
                if (leadingNewline) {
                    contentToWrite = "\n" + fileContent;
                }
                if (trailingNewline) {
                    contentToWrite = fileContent + "\n";
                }

                long bytesWritten = 0;
                if (sudo) {
                    // 创建临时文件
                    Path tempFile = Files.createTempFile("file_write_", ".tmp");
                    Files.write(tempFile, contentToWrite.getBytes(StandardCharsets.UTF_8));

                    ProcessBuilder pb = new ProcessBuilder("sudo", "bash", "-c",
                            "cat " + tempFile.toString() + " " + (append ? ">>" : ">") + " '" + file + "'");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 日志记录
                        }
                    }

                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new BadRequestException("Failed to write file with sudo");
                    }

                    Files.deleteIfExists(tempFile);
                    bytesWritten = contentToWrite.getBytes(StandardCharsets.UTF_8).length;
                } else {
                    // 创建目录
                    Path path = Paths.get(file).getParent();
                    if (path != null && !Files.exists(path)) {
                        Files.createDirectories(path);
                    }

                    // 写入文件
                    OpenOption[] options = append ?
                            new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND} :
                            new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

                    bytesWritten = Files.write(Paths.get(file), contentToWrite.getBytes(StandardCharsets.UTF_8), options).toFile().length();
                }

                return new FileWriteResult(file, bytesWritten);
            } catch (Exception e) {
                if (e instanceof BadRequestException) {
                    throw new CompletionException(e);
                }
                throw new CompletionException(new AppException("Failed to write file: " + e.getMessage()));
            }
        });
    }

    @Async("fileTaskExecutor")
    public CompletableFuture<FileReplaceResult> replaceString(String file, String oldStr,
                                                              String newStr, boolean sudo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 读取文件
                FileReadResult result = readFile(file, null, null, sudo).get();
                String content = result.getContent();

                // 计算替换次数
                int replacedCount = countOccurrences(content, oldStr);
                if (replacedCount == 0) {
                    return new FileReplaceResult(file, 0);
                }

                // 替换内容
                String newContent = content.replace(oldStr, newStr);

                // 写回文件
                writeFile(file, newContent, false, false, false, sudo).get();

                return new FileReplaceResult(file, replacedCount);
            } catch (Exception e) {
                throw new CompletionException(new AppException("Failed to replace string: " + e.getMessage()));
            }
        });
    }

    private int countOccurrences(String content, String sub) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }
        return count;
    }

    @Async("fileTaskExecutor")
    public CompletableFuture<FileSearchResult> searchInContent(String file, String regex, boolean sudo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 读取文件
                FileReadResult result = readFile(file, null, null, sudo).get();
                String content = result.getContent();

                // 编译正则表达式
                Pattern pattern;
                try {
                    pattern = Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    throw new BadRequestException("Invalid regular expression: " + e.getDescription());
                }

                // 查找匹配项
                List<String> matches = new ArrayList<>();
                List<Integer> lineNumbers = new ArrayList<>();

                String[] lines = content.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (pattern.matcher(lines[i]).find()) {
                        matches.add(lines[i]);
                        lineNumbers.add(i);
                    }
                }

                return new FileSearchResult(file, matches, lineNumbers);
            } catch (Exception e) {
                if (e instanceof BadRequestException) {
                    throw new CompletionException(e);
                }
                throw new CompletionException(new AppException("Failed to search in content: " + e.getMessage()));
            }
        });
    }

    @Async("fileTaskExecutor")
    public CompletableFuture<FileFindResult> findFilesByName(String path, String globPattern) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(Paths.get(path))) {
                    throw new ResourceNotFoundException("Directory does not exist: " + path);
                }

                // 使用Glob模式查找文件
                List<String> files = new ArrayList<>();
                Path rootPath = Paths.get(path);
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

                Files.walk(rootPath)
                        .filter(pathObj -> matcher.matches(pathObj.getFileName()))
                        .forEach(pathObj -> files.add(pathObj.toString()));

                return new FileFindResult(path, files);
            } catch (Exception e) {
                if (e instanceof ResourceNotFoundException) {
                    throw new CompletionException(e);
                }
                throw new CompletionException(new AppException("Failed to find files: " + e.getMessage()));
            }
        });
    }

}
