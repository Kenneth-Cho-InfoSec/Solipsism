package com.krystelligence.solipsism.adblock.source

/**
 * The provider for the hosts data source.
 */
interface HostsDataSourceProvider {

    /**
     * Create the hosts data source.
     */
    fun createHostsDataSource(): HostsDataSource

}
