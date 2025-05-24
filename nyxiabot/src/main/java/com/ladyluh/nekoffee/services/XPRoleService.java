package com.ladyluh.nekoffee.services;

import com.ladyluh.nekoffee.api.NekoffeeClient;
import com.ladyluh.nekoffee.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

public class XPRoleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XPRoleService.class);
    private final NekoffeeClient client;
    private final TreeMap<Integer, String> xpRoleMappings; 

    public XPRoleService(NekoffeeClient client, ConfigManager config) {
        this.client = client;
        this.xpRoleMappings = new TreeMap<>(config.getXPRoleMappings());
        if (this.xpRoleMappings.isEmpty()) {
            LOGGER.warn("Nenhum mapeamento de XP Role encontrado na configuração. Cargos de XP desabilitados.");
        }
    }

    /**
     * Encontra o ID do cargo de XP mais alto aplicável para um determinado nível.
     * @param level O nível atual do usuário.
     * @return O ID do cargo (String) ou null se nenhum cargo for definido para este nível ou níveis anteriores.
     */
    public String getHighestApplicableRole(int level) {
        
        Map.Entry<Integer, String> entry = xpRoleMappings.floorEntry(level);
        if (entry != null) {
            return entry.getValue();
        }
        return null; 
    }

    /**
     * Atribui o cargo de XP apropriado a um membro e remove o cargo XP anterior (se aplicável).
     * Chamado quando um membro sobe de nível.
     * @param guildId O ID do servidor.
     * @param userId O ID do usuário.
     * @param oldLevel O nível do usuário ANTES da atualização.
     * @param newLevel O nível do usuário APÓS a atualização.
     * @return Um CompletableFuture que é completado após a tentativa de atribuição/remoção de cargos.
     */
    public CompletableFuture<Void> assignXPRoles(String guildId, String userId, int oldLevel, int newLevel) {
        if (xpRoleMappings.isEmpty()) {
            return CompletableFuture.completedFuture(null); 
        }

        String roleIdToAssign = getHighestApplicableRole(newLevel);
        String roleIdToRemove = getHighestApplicableRole(oldLevel);

        LOGGER.info("Atribuição de XP Role para usuário {} (Guild {}): Nível {} -> {}. Cargo a atribuir: {}. Cargo a remover: {}",
                userId, guildId, oldLevel, newLevel, roleIdToAssign, roleIdToRemove);

        CompletableFuture<Void> addRoleFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> removeRoleFuture = CompletableFuture.completedFuture(null);

        
        if (roleIdToAssign != null && !roleIdToAssign.equals(roleIdToRemove)) {
            addRoleFuture = client.addRoleToMember(guildId, userId, roleIdToAssign)
                    .thenRun(() -> LOGGER.info("Cargo de XP {} atribuído a {} em {}.", roleIdToAssign, userId, guildId))
                    .exceptionally(ex -> {
                        LOGGER.error("Falha ao atribuir cargo de XP {} a {} em {}:", roleIdToAssign, userId, guildId, ex);
                        return null;
                    });
        } else if (roleIdToAssign == null && roleIdToRemove != null) {
            
            LOGGER.debug("Usuário {} não tem cargo de XP aplicável no nível {}. Removendo cargo antigo {}.", userId, newLevel, roleIdToRemove);
        } else if (roleIdToAssign != null) {
            LOGGER.debug("Cargo de XP não precisa ser alterado para {}. Permanece {}.", userId, roleIdToAssign);
            return CompletableFuture.completedFuture(null); 
        }

        
        
        if (roleIdToRemove != null) {
            removeRoleFuture = client.removeRoleFromMember(guildId, userId, roleIdToRemove)
                    .thenRun(() -> LOGGER.info("Cargo de XP {} removido de {} em {}.", roleIdToRemove, userId, guildId))
                    .exceptionally(ex -> {
                        LOGGER.error("Falha ao remover cargo de XP {} de {} em {}:", roleIdToRemove, userId, guildId, ex);
                        return null;
                    });
        }

        return CompletableFuture.allOf(addRoleFuture, removeRoleFuture);
    }
}