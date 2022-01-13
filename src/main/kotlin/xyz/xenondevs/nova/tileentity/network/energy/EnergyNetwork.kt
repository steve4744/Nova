package xyz.xenondevs.nova.tileentity.network.energy

import org.bukkit.block.BlockFace
import xyz.xenondevs.nova.tileentity.network.*
import xyz.xenondevs.nova.tileentity.network.energy.EnergyConnectionType.*
import xyz.xenondevs.nova.tileentity.network.energy.holder.EnergyHolder
import xyz.xenondevs.nova.util.sumOfNoOverflow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * An EnergyNetwork consists of [NetworkBridge] that connect [NetworkEndPoint]
 * and their [EnergyHolder].<br>
 * [EnergyHolders][EnergyHolder] can provide, consume or buffer energy.
 */
class EnergyNetwork : Network {
    
    override val type = NetworkType.ENERGY
    
    override val nodes: Set<NetworkNode>
        get() = _nodes
    
    private val _nodes = HashSet<NetworkNode>()
    private val bridges = HashSet<EnergyBridge>()
    private val providers = HashSet<EnergyHolder>()
    private val consumers = HashSet<EnergyHolder>()
    private val buffers = HashSet<EnergyHolder>()
    
    private val availableProviderEnergy: Long
        get() = providers.sumOfNoOverflow { it.energy }
    private val availableBufferEnergy: Long
        get() = buffers.sumOfNoOverflow { it.energy }
    private val requestedConsumerEnergy: Long
        get() = consumers.sumOfNoOverflow { it.requestedEnergy }
    private var transferRate = Long.MAX_VALUE
    
    override fun addAll(network: Network) {
        require(network !== this) { "Can't add to self" }
        require(network is EnergyNetwork) { "Illegal Network Type" }
        
        _nodes += network._nodes
        providers += network.providers
        consumers += network.consumers
        bridges += network.bridges
        buffers += network.buffers
    }
    
    override fun addBridge(bridge: NetworkBridge) {
        require(bridge is EnergyBridge) { "Illegal Bridge Type" }
        _nodes += bridge
        bridges += bridge
        transferRate = bridge.energyTransferRate
    }
    
    override fun addEndPoint(endPoint: NetworkEndPoint, face: BlockFace) {
        val holder = endPoint.holders[NetworkType.ENERGY] as EnergyHolder
        
        when (val connectionType = holder.energyConfig[face]!!) {
            
            PROVIDE -> {
                if (!buffers.contains(holder)) {
                    if (consumers.contains(holder)) {
                        consumers -= holder
                        buffers += holder
                    } else {
                        providers += holder
                    }
                }
            }
            
            CONSUME -> {
                if (!buffers.contains(holder)) {
                    if (providers.contains(holder)) {
                        providers -= holder
                        buffers += holder
                    } else {
                        consumers += holder
                    }
                }
            }
            
            BUFFER -> {
                removeNode(endPoint) // remove from provider / consumer set
                buffers += holder
            }
            
            else -> throw IllegalArgumentException("Illegal ConnectionType: $connectionType")
            
        }
        
        _nodes += endPoint
    }
    
    override fun removeNode(node: NetworkNode) {
        _nodes -= node
        if (node is NetworkEndPoint) {
            val holder = node.holders[NetworkType.ENERGY] as EnergyHolder
            providers -= holder
            consumers -= holder
            buffers -= holder
        } else if (node is EnergyBridge) {
            bridges -= node
        }
    }
    
    override fun isEmpty() = _nodes.isEmpty()
    
    override fun isValid() = bridges.isNotEmpty() || ((providers.isNotEmpty() && consumers.isNotEmpty()) || (buffers.isNotEmpty() && (providers.isNotEmpty() || consumers.isNotEmpty())))
    
    /**
     * Called every tick to transfer energy.
     */
    override fun handleTick() {
        val providerEnergy = min(transferRate, availableProviderEnergy)
        val bufferEnergy = min(transferRate - providerEnergy, availableBufferEnergy)
        val requestedEnergy = min(transferRate, requestedConsumerEnergy)
        
        val useBuffers = requestedEnergy > providerEnergy
        
        val availableEnergy = providerEnergy + if (useBuffers) bufferEnergy else 0
        
        var energy = availableEnergy
        energy = distributeEqually(energy, consumers)
        if (!useBuffers && energy > 0) energy = distributeEqually(energy, buffers) // didn't take energy from buffers, can fill them up
        
        var energyDeficit = availableEnergy - energy
        energyDeficit = takeEqually(energyDeficit, providers)
        if (energyDeficit != 0L && useBuffers) energyDeficit = takeEqually(energyDeficit, buffers)
        
        if (energyDeficit != 0L) throw NetworkException("Not enough energy: $energyDeficit") // should never happen
    }
    
    private fun distributeEqually(energy: Long, consumers: Iterable<EnergyHolder>): Long {
        var availableEnergy = energy
        
        val consumerMap = ConcurrentHashMap<EnergyHolder, Long>()
        consumerMap += consumers
            .filterNot { it.requestedEnergy == 0L }
            .map { it to it.requestedEnergy }
        
        while (availableEnergy != 0L && consumerMap.isNotEmpty()) {
            val distribution = availableEnergy / consumerMap.size
            if (distribution == 0L) break
            if (distribution != 0L) {
                for ((consumer, requestedAmount) in consumerMap) {
                    val energyToGive = min(distribution, requestedAmount)
                    consumer.energy += energyToGive
                    if (energyToGive == requestedAmount) consumerMap -= consumer // consumer is satisfied
                    else consumerMap[consumer] = requestedAmount - energyToGive // consumer is not satisfied
                    availableEnergy -= energyToGive
                }
            } else {
                // can't split up equally
                return giveFirst(availableEnergy, consumers)
            }
        }
        
        return availableEnergy
    }
    
    private fun giveFirst(energy: Long, consumers: Iterable<EnergyHolder>): Long {
        var availableEnergy = energy
        for (consumer in consumers) {
            val energyToGive = min(availableEnergy, consumer.requestedEnergy)
            availableEnergy -= energyToGive
            consumer.energy += energyToGive
            
            if (availableEnergy == 0L) break
        }
        
        return availableEnergy
    }
    
    private fun takeEqually(energy: Long, providers: Iterable<EnergyHolder>): Long {
        var energyDeficit = energy
        
        val providerMap = ConcurrentHashMap<EnergyHolder, Long>()
        providerMap += providers
            .filterNot { it.energy == 0L }
            .map { it to it.energy }
        
        while (energyDeficit != 0L && providerMap.isNotEmpty()) {
            val distribution = energyDeficit / providerMap.size
            if (distribution != 0L) {
                for ((provider, providedAmount) in providerMap) {
                    val take = min(distribution, providedAmount)
                    energyDeficit -= take
                    provider.energy -= take
                    if (take == providedAmount) providerMap -= provider // provider has no more energy
                    else providerMap[provider] = providedAmount - take // provider has less energy
                }
            } else {
                // can't split up equally
                return takeFirst(energyDeficit, providers)
            }
        }
        
        return energyDeficit
    }
    
    private fun takeFirst(energy: Long, providers: Iterable<EnergyHolder>): Long {
        var energyDeficit = energy
        for (provider in providers) {
            val take = min(energyDeficit, provider.energy)
            energyDeficit -= take
            provider.energy -= take
            
            if (energyDeficit == 0L) break
        }
        
        return energyDeficit
    }
    
}