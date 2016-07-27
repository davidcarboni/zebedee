package com.github.onsdigital.zebedee.service.content.navigation;

import com.github.onsdigital.zebedee.json.ContentDetail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Provide functionality for traversing the browse tree to find nodes by their URI.
 */
public class ContentTreeNavigator {

    private static final Path ROOT = Paths.get("/");
    private static final Path DATA_JSON = Paths.get("data.json");

    private static ContentTreeNavigator instance = null;

    public static ContentTreeNavigator getInstance() {
        if (instance == null) {
            instance = new ContentTreeNavigator();
        }
        return instance;
    }

    private ContentTreeNavigator() {
    }

    public boolean updateNodeAndDescendants(ContentDetail browseTree, Path targetNodeUri, ContentDetailModifier function) {
        // find the parent node to update.
        Optional<ContentDetail> targetNode = findContentDetail(browseTree, targetNodeUri);

        if (targetNode.isPresent()) {
            applyAndPropagate(targetNode.get(), function);
            return true;
        }
        return false;
    }

    private void applyAndPropagate(ContentDetail node, ContentDetailModifier function) {
        if (node != null) {
            function.apply(node);
            if (node.children != null || !node.children.isEmpty()) {
                node.children.stream().forEach(child -> applyAndPropagate(child, function));
            }
        }
    }

    public boolean updateNode(ContentDetail browseTree, Path targetNodeUri, ContentDetailModifier function) {
        Optional<ContentDetail> targetNode = findContentDetail(browseTree, targetNodeUri);

        if (targetNode.isPresent()) {
            function.apply(targetNode.get());
            return true;
        }
        return false;
    }

    public Optional<ContentDetail> findContentDetail(ContentDetail browseTree, Path targetNodeUri) {
        Iterator<Path> pathIterator = createPathIterator(targetNodeUri);
        return find(pathIterator, browseTree);
    }

    /**
     * Builds an {@link Iterator} of {@link Path}'s from the input path. The path is spilt at each directory level
     * and returned ordered root to most specific dir. For example if the input "/home/business/industry" then method
     * will return Iterator["/home", "/home/business", "/home/business/industry"].
     */
    private Iterator<Path> createPathIterator(Path targetNodeUri) {
        Path tempPath = requireNonNull(targetNodeUri, "targetNodeUri is required and cannot be null");
        List<Path> segments = new ArrayList<>();

        while (tempPath.getParent() != null) {
            if (!tempPath.equals(ROOT)) {
                if (!tempPath.getFileName().equals(DATA_JSON)) {
                    segments.add(ROOT.resolve(tempPath));
                }
            }
            tempPath = tempPath.getParent();
        }
        Collections.reverse(segments);
        return segments.iterator();
    }

    private Optional<ContentDetail> find(Iterator<Path> pathIterator, ContentDetail node) {
        if (node == null || node.children == null || node.children.isEmpty()) {
            return null;
        }

        Path pathToSearchFor = pathIterator.next();
        Optional<ContentDetail> childNode = findChild(node, pathToSearchFor);

        if (childNode.isPresent()) {
            if (pathIterator.hasNext()) {
                return find(pathIterator, childNode.get());
            }
            return childNode;
        }
        // If child node not found give up
        return Optional.empty();
    }

    private Optional<ContentDetail> findChild(ContentDetail node, Path uri) {
        return node.children.stream().filter(child -> {
            if (child.contentPath == null || uri == null) {
                return false;
            }
            return child.contentPath.equals(uri.toString());
        }).findFirst();
    }
}
