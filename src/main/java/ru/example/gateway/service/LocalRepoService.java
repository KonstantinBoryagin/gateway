package ru.example.gateway.service;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class LocalRepoService {

    @Value("${gitlab.username}")
    private String gitlabUsername;

    @Value("${gitlab.password}")
    private String gitlabPassword;

    @Value("${gitlab.localRepo}")
    private String pathToLocalRepo;

    @Value("${gitlab.remoteRepo}")
    private String pathToRemoteRepo;

    /**
     * Проверяет, существует ли локальный репозиторий и либо обновляет его, либо клонирует из GitLab
     * @throws GitAPIException исключение
     * @throws IOException исключение
     */
    public void getRepoToLocal() throws GitAPIException, IOException {
        Path repoPath = Paths.get(pathToLocalRepo);
        if (Files.exists(repoPath)) {
            //PULL
            Git git = Git.open(repoPath.toFile());
                git
                    .pull()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitlabUsername, gitlabPassword))
                    .call();

        } else {
            //CLONE
            Git.cloneRepository()
                    .setURI(pathToRemoteRepo)
                    .setDirectory(new File(pathToLocalRepo))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitlabUsername, gitlabPassword))
                    .setCloneAllBranches(true)
                    .call();
        }
        log.debug("pull/clone repository to disk {}", pathToLocalRepo);
    }


    /**
     * Вычитывает из локального репозитория все файлы с расширением .json и формирует Map из полного имени файла
     * (относительно папки репозитория) и его содержимым
     * @return Map с полным именем файла и строковым представлением его содержимого
     * @throws IOException исключение
     */
    public Map<String, String> formTextSchemasFromLocalRepo() throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(pathToLocalRepo))) {
            List<File> collect = paths
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().toLowerCase().endsWith(".json"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());

            Map<String, String> textSchemasFromLocalRepo = new HashMap<>();
            for (File elem : collect) {
                try (BufferedReader bufferedReader = new BufferedReader(new FileReader(elem))) {
                    textSchemasFromLocalRepo.put(formPathToJsonFile(elem.getAbsolutePath()), bufferedReader.lines().collect(Collectors.joining()));
                }
            }
            return textSchemasFromLocalRepo;
        }
    }

    /**
     * Формирует путь к файлу относительно локального репозитория из полного пути
     * @param absolutePath полный путь к файлу
     * @return путь относительно локального репозитория
     */
    private String formPathToJsonFile(String absolutePath) {
        return absolutePath.replaceFirst(pathToLocalRepo, "").toLowerCase();
    }
}

