package coop.rchain.casper.util

import coop.rchain.casper.helper.{BlockDagStorageFixture, BlockGenerator}
import coop.rchain.casper.helper.BlockGenerator._
import coop.rchain.casper.helper.BlockUtil.generateValidator
import coop.rchain.casper.protocol.BlockMessage
import coop.rchain.shared.scalatestcontrib._
import coop.rchain.casper.util.ProtoUtil._
import coop.rchain.models.BlockMetadata
import monix.eval.Task
import org.scalatest.{FlatSpec, Matchers}

class CasperUtilTest
    extends FlatSpec
    with Matchers
    with BlockGenerator
    with BlockDagStorageFixture {
  implicit private def blockMessageToBlockMetadata(block: BlockMessage): BlockMetadata =
    BlockMetadata.fromBlock(block, invalid = false)

  "isInMainChain" should "classify appropriately" in withStorage {
    implicit blockStore => implicit blockDagStorage =>
      for {
        genesis <- createGenesis[Task]()
        b2      <- createBlock[Task](Seq(genesis.blockHash), genesis)
        b3      <- createBlock[Task](Seq(b2.blockHash), genesis)

        dag <- blockDagStorage.getRepresentation

        _      <- isInMainChain(dag, genesis, b3.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b2, b3.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b3, b2.blockHash) shouldBeF false
        result <- isInMainChain(dag, b3, genesis.blockHash) shouldBeF false
      } yield result
  }

  "isInMainChain" should "classify diamond DAGs appropriately" in withStorage {
    implicit blockStore => implicit blockDagStorage =>
      for {
        genesis <- createGenesis[Task]()
        b2      <- createBlock[Task](Seq(genesis.blockHash), genesis)
        b3      <- createBlock[Task](Seq(genesis.blockHash), genesis)
        b4      <- createBlock[Task](Seq(b2.blockHash, b3.blockHash), genesis)

        dag <- blockDagStorage.getRepresentation

        _      <- isInMainChain(dag, genesis, b2.blockHash) shouldBeF true
        _      <- isInMainChain(dag, genesis, b3.blockHash) shouldBeF true
        _      <- isInMainChain(dag, genesis, b4.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b2, b4.blockHash) shouldBeF true
        result <- isInMainChain(dag, b3, b4.blockHash) shouldBeF false
      } yield result
  }

  // See https://docs.google.com/presentation/d/1znz01SF1ljriPzbMoFV0J127ryPglUYLFyhvsb-ftQk/edit?usp=sharing slide 29 for diagram
  "isInMainChain" should "classify complicated chains appropriately" in withStorage {
    implicit blockStore => implicit blockDagStorage =>
      val v1 = generateValidator("Validator One")
      val v2 = generateValidator("Validator Two")

      for {
        genesis <- createGenesis[Task]()
        b2      <- createBlock[Task](Seq(genesis.blockHash), genesis, v2)
        b3      <- createBlock[Task](Seq(genesis.blockHash), genesis, v1)
        b4      <- createBlock[Task](Seq(b2.blockHash), genesis, v2)
        b5      <- createBlock[Task](Seq(b2.blockHash), genesis, v1)
        b6      <- createBlock[Task](Seq(b4.blockHash), genesis, v2)
        b7      <- createBlock[Task](Seq(b4.blockHash), genesis, v1)
        b8      <- createBlock[Task](Seq(b7.blockHash), genesis, v1)

        dag <- blockDagStorage.getRepresentation

        _      <- isInMainChain(dag, genesis, b2.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b2, b3.blockHash) shouldBeF false
        _      <- isInMainChain(dag, b3, b4.blockHash) shouldBeF false
        _      <- isInMainChain(dag, b4, b5.blockHash) shouldBeF false
        _      <- isInMainChain(dag, b5, b6.blockHash) shouldBeF false
        _      <- isInMainChain(dag, b6, b7.blockHash) shouldBeF false
        _      <- isInMainChain(dag, b7, b8.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b2, b6.blockHash) shouldBeF true
        _      <- isInMainChain(dag, b2, b8.blockHash) shouldBeF true
        result <- isInMainChain(dag, b4, b2.blockHash) shouldBeF false
      } yield result
  }
}
