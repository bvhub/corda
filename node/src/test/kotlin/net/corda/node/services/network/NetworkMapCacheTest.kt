package net.corda.node.services.network

import net.corda.core.utilities.getOrThrow
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class NetworkMapCacheTest {
    lateinit var mockNet: MockNetwork

    @Before
    fun setUp() {
        mockNet = MockNetwork()
    }

    @After
    fun teardown() {
        mockNet.stopNodes()
    }

    @Test
    fun registerWithNetwork() {
        val mapNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(mapNode.network.myAddress, ALICE.name)
        val future = aliceNode.services.networkMapCache.addMapService(aliceNode.network, mapNode.network.myAddress, false, null)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val aliceNode = mockNet.createNode(nodeFactory = MockNetwork.DefaultFactory, legalName = ALICE.name, entropyRoot = entropy)
        val bobNode = mockNet.createNode(nodeFactory = MockNetwork.DefaultFactory, legalName = BOB.name, entropyRoot = entropy)
        assertEquals(aliceNode.info.chooseIdentity(), bobNode.info.chooseIdentity())

        mockNet.runNetwork()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), aliceNode.info)

        aliceNode.services.networkMapCache.addNode(bobNode.info)
        // The details of node B write over those for node A
        assertEquals(aliceNode.services.networkMapCache.getNodesByLegalIdentityKey(aliceNode.info.chooseIdentity().owningKey).singleOrNull(), bobNode.info)
    }

    @Test
    fun `getNodeByLegalIdentity`() {
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val notaryLookup = notaryNode.nodeLookup
        val expected = aliceNode.info

        mockNet.runNetwork()
        val actual = notaryNode.database.transaction { notaryLookup.getNodeByLegalIdentity(aliceNode.info.chooseIdentity()) }
        assertEquals(expected, actual)

        // TODO: Should have a test case with anonymous lookup
    }

    @Test
    fun `remove node from cache`() {
        val notaryNode = mockNet.createNotaryNode()
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val notaryLegalIdentity = notaryNode.info.chooseIdentity()
        val alice = aliceNode.info.chooseIdentity()
        val notaryLookup = notaryNode.nodeLookup
        val notaryCache = notaryNode.services.networkMapCache as PersistentNetworkMapCache
        mockNet.runNetwork()
        notaryNode.database.transaction {
            assertThat(notaryLookup.getNodeByLegalIdentity(alice) != null)
            notaryCache.removeNode(aliceNode.info)
            assertThat(notaryLookup.getNodeByLegalIdentity(alice) == null)
            assertThat(notaryLookup.getNodeByLegalIdentity(notaryLegalIdentity) != null)
            assertThat(notaryCache.getNodeByLegalName(alice.name) == null)
        }
    }
}
