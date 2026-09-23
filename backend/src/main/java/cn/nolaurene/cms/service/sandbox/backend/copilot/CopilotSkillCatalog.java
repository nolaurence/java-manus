package cn.nolaurene.cms.service.sandbox.backend.copilot;

import cn.nolaurene.cms.service.sandbox.backend.skill.SkillFileStorageService;
import cn.nolaurene.cms.service.sandbox.backend.skill.SkillToolProvider;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

/**
 * Resolves database-enabled skills into the filesystem contract used by the
 * Copilot-style loop.  The catalog creates a per-run view so one user's skills
 * cannot be discovered from another user's shared storage root.
 */
@Service
public class CopilotSkillCatalog {

    @Resource
    private SkillToolProvider skillToolProvider;

    @Resource
    private SkillFileStorageService skillFileStorageService;

    public Selection prepare(Long userId) throws IOException {
        return prepare(userId, List.of());
    }

    /** Prepare a user-scoped skill root with optional Copilot-style disables. */
    public Selection prepare(Long userId, Collection<String> disabledSkills) throws IOException {
        List<String> enabledIds = skillToolProvider.getEnabledSkillIdsForUser(userId);
        String stagedRoot = skillFileStorageService.createCopilotSkillDirectory(enabledIds);
        try {
            List<CopilotSkillDescriptor> descriptors = new CopilotSkillLoader(
                    Paths.get(stagedRoot), disabledSkills).load();
            return new Selection(stagedRoot, enabledIds, descriptors, skillFileStorageService);
        } catch (RuntimeException | Error error) {
            skillFileStorageService.deleteCopilotSkillDirectory(stagedRoot);
            throw error;
        }
    }

    public static final class Selection implements AutoCloseable {
        private final String root;
        private final List<String> enabledIds;
        private final List<CopilotSkillDescriptor> skills;
        private final SkillFileStorageService storage;

        private Selection(String root,
                          Collection<String> enabledIds,
                          Collection<CopilotSkillDescriptor> skills,
                          SkillFileStorageService storage) {
            this.root = root;
            this.enabledIds = enabledIds == null ? List.of() : List.copyOf(enabledIds);
            this.skills = skills == null ? List.of() : List.copyOf(skills);
            this.storage = storage;
        }

        public String root() { return root; }
        public String getRoot() { return root; }
        public List<String> enabledIds() { return enabledIds; }
        public List<String> getEnabledIds() { return enabledIds; }
        public List<CopilotSkillDescriptor> skills() { return skills; }
        public List<CopilotSkillDescriptor> getSkills() { return skills; }

        @Override
        public void close() {
            if (storage != null) {
                storage.deleteCopilotSkillDirectory(root);
            }
        }
    }
}
