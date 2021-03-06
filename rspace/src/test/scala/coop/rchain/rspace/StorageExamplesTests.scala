package coop.rchain.rspace

import coop.rchain.rspace.examples.StringExamples.StringsCaptor
import java.nio.file.{Files, Path}

import cats._
import cats.implicits._
import cats.effect.Sync
import coop.rchain.rspace.examples.AddressBookExample._
import coop.rchain.rspace.examples.AddressBookExample.implicits._
import coop.rchain.rspace.history.{initialize, Branch, ITrieStore, InMemoryTrieStore, LMDBTrieStore}
import coop.rchain.rspace.internal.{codecGNAT, GNAT}
import coop.rchain.rspace.util._
import coop.rchain.shared.PathOps._
import monix.eval.Coeval
import org.scalatest.BeforeAndAfterAll
import scodec.Codec

import scala.concurrent.ExecutionContext.Implicits.global

trait StorageExamplesTests[F[_]]
    extends StorageTestsBase[F, Channel, Pattern, Entry, EntriesCaptor]
    with TestImplicitHelpers {

  "CORE-365: A joined consume on duplicate channels followed by two produces on that channel" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space
             .consume(
               List(Channel("friends"), Channel("friends")),
               List(CityMatch(city = "Crystal Lake"), CityMatch(city = "Crystal Lake")),
               new EntriesCaptor,
               persist = false
             )
      _  = r1 shouldBe None
      r2 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r2 shouldBe None
      r3 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r3 shouldBe defined
      _  = runK(r3)
      _  = getK(r3).results shouldBe List(List(bob, bob))
    } yield (store.isEmpty shouldBe true)
  }

  "CORE-365: Two produces on the same channel followed by a joined consume on duplicates of that channel" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r1 shouldBe None
      r2 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r2 shouldBe None
      r3 <- space
             .consume(
               List(Channel("friends"), Channel("friends")),
               List(CityMatch(city = "Crystal Lake"), CityMatch(city = "Crystal Lake")),
               new EntriesCaptor,
               persist = false
             )
      _ = r3 shouldBe defined
      _ = runK(r3)
      _ = getK(r3).results shouldBe List(List(bob, bob))
    } yield (store.isEmpty shouldBe true)
  }

  "CORE-365: A joined consume on duplicate channels given twice followed by three produces" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space
             .consume(
               List(Channel("colleagues"), Channel("friends"), Channel("friends")),
               List(
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake")
               ),
               new EntriesCaptor,
               persist = false
             )
      _  = r1 shouldBe None
      r2 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r2 shouldBe None
      r3 <- space.produce(Channel("friends"), bob, persist = false)
      _  = r3 shouldBe None
      r4 <- space.produce(Channel("colleagues"), alice, persist = false)
      _  = r4 shouldBe defined
      _  = runK(r4)
      _  = getK(r4).results shouldBe List(List(alice, bob, bob))
    } yield (store.isEmpty shouldBe true)

  }

  "CORE-365: A joined consume on multiple duplicate channels followed by the requisite produces" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space
             .consume(
               List(
                 Channel("family"),
                 Channel("family"),
                 Channel("family"),
                 Channel("family"),
                 Channel("colleagues"),
                 Channel("colleagues"),
                 Channel("colleagues"),
                 Channel("friends"),
                 Channel("friends")
               ),
               List(
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake")
               ),
               new EntriesCaptor,
               persist = false
             )
      _   = r1 shouldBe None
      r2  <- space.produce(Channel("friends"), bob, persist = false)
      r3  <- space.produce(Channel("family"), carol, persist = false)
      r4  <- space.produce(Channel("colleagues"), alice, persist = false)
      r5  <- space.produce(Channel("friends"), bob, persist = false)
      r6  <- space.produce(Channel("family"), carol, persist = false)
      r7  <- space.produce(Channel("colleagues"), alice, persist = false)
      r8  <- space.produce(Channel("colleagues"), alice, persist = false)
      r9  <- space.produce(Channel("family"), carol, persist = false)
      r10 <- space.produce(Channel("family"), carol, persist = false)

      _ = r2 shouldBe None
      _ = r3 shouldBe None
      _ = r4 shouldBe None
      _ = r5 shouldBe None
      _ = r6 shouldBe None
      _ = r7 shouldBe None
      _ = r8 shouldBe None
      _ = r9 shouldBe None
      _ = r10 shouldBe defined

      _ = runK(r10)
      _ = getK(r10).results shouldBe List(
        List(carol, carol, carol, carol, alice, alice, alice, bob, bob)
      )

    } yield (store.isEmpty shouldBe true)
  }

  "CORE-365: Multiple produces on multiple duplicate channels followed by the requisite consume" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space.produce(Channel("friends"), bob, persist = false)
      r2 <- space.produce(Channel("family"), carol, persist = false)
      r3 <- space.produce(Channel("colleagues"), alice, persist = false)
      r4 <- space.produce(Channel("friends"), bob, persist = false)
      r5 <- space.produce(Channel("family"), carol, persist = false)
      r6 <- space.produce(Channel("colleagues"), alice, persist = false)
      r7 <- space.produce(Channel("colleagues"), alice, persist = false)
      r8 <- space.produce(Channel("family"), carol, persist = false)
      r9 <- space.produce(Channel("family"), carol, persist = false)

      _ = r1 shouldBe None
      _ = r2 shouldBe None
      _ = r3 shouldBe None
      _ = r4 shouldBe None
      _ = r5 shouldBe None
      _ = r6 shouldBe None
      _ = r7 shouldBe None
      _ = r8 shouldBe None
      _ = r9 shouldBe None

      r10 <- space
              .consume(
                List(
                  Channel("family"),
                  Channel("family"),
                  Channel("family"),
                  Channel("family"),
                  Channel("colleagues"),
                  Channel("colleagues"),
                  Channel("colleagues"),
                  Channel("friends"),
                  Channel("friends")
                ),
                List(
                  CityMatch(city = "Herbert"),
                  CityMatch(city = "Herbert"),
                  CityMatch(city = "Herbert"),
                  CityMatch(city = "Herbert"),
                  CityMatch(city = "Crystal Lake"),
                  CityMatch(city = "Crystal Lake"),
                  CityMatch(city = "Crystal Lake"),
                  CityMatch(city = "Crystal Lake"),
                  CityMatch(city = "Crystal Lake")
                ),
                new EntriesCaptor,
                persist = false
              )

      _ = r10 shouldBe defined
      _ = runK(r10)
      _ = getK(r10).results shouldBe List(
        List(carol, carol, carol, carol, alice, alice, alice, bob, bob)
      )
    } yield (store.isEmpty shouldBe true)
  }

  "CORE-365: A joined consume on multiple mixed up duplicate channels followed by the requisite produces" should
    "return a continuation and the produced data" in withTestSpace { (store, space) =>
    for {
      r1 <- space
             .consume(
               List(
                 Channel("family"),
                 Channel("colleagues"),
                 Channel("family"),
                 Channel("friends"),
                 Channel("friends"),
                 Channel("family"),
                 Channel("colleagues"),
                 Channel("colleagues"),
                 Channel("family")
               ),
               List(
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Herbert"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Crystal Lake"),
                 CityMatch(city = "Herbert")
               ),
               new EntriesCaptor,
               persist = false
             )

      _ = r1 shouldBe None

      r2  <- space.produce(Channel("friends"), bob, persist = false)
      r3  <- space.produce(Channel("family"), carol, persist = false)
      r4  <- space.produce(Channel("colleagues"), alice, persist = false)
      r5  <- space.produce(Channel("friends"), bob, persist = false)
      r6  <- space.produce(Channel("family"), carol, persist = false)
      r7  <- space.produce(Channel("colleagues"), alice, persist = false)
      r8  <- space.produce(Channel("colleagues"), alice, persist = false)
      r9  <- space.produce(Channel("family"), carol, persist = false)
      r10 <- space.produce(Channel("family"), carol, persist = false)

      _ = r2 shouldBe None
      _ = r3 shouldBe None
      _ = r4 shouldBe None
      _ = r5 shouldBe None
      _ = r6 shouldBe None
      _ = r7 shouldBe None
      _ = r8 shouldBe None
      _ = r9 shouldBe None
      _ = r10 shouldBe defined

      _ = runK(r10)
      _ = getK(r10).results shouldBe List(
        List(carol, alice, carol, bob, bob, carol, alice, alice, carol)
      )
    } yield (store.isEmpty shouldBe true)
  }
}

abstract class MixedInMemoryStoreStorageExamplesTestsBase[F[_]]
    extends StorageTestsBase[F, Channel, Pattern, Entry, EntriesCaptor]
    with BeforeAndAfterAll {

  val dbDir: Path   = Files.createTempDirectory("rchain-storage-test-")
  val mapSize: Long = 1024L * 1024L * 1024L

  override def withTestSpace[R](f: (ST, T) => F[R]): R = {

    implicit val cg: Codec[GNAT[Channel, Pattern, Entry, EntriesCaptor]] = codecGNAT(
      serializeChannel.toCodec,
      serializePattern.toCodec,
      serializeInfo.toCodec,
      serializeEntriesCaptor.toCodec
    )

    val branch = Branch("inmem")

    val env = Context.env(dbDir, mapSize)

    val trieStore = LMDBTrieStore
      .create[F, Blake2b256Hash, GNAT[Channel, Pattern, Entry, EntriesCaptor]](env, dbDir)
    val testStore = LockFreeInMemoryStore.create(trieStore, branch)

    run(for {
      testSpace <- RSpace.create[F, Channel, Pattern, Entry, Entry, EntriesCaptor](
                    testStore,
                    branch
                  )
      //_         <- testStore.withWriteTxnF(testStore.clear)
      //_         = trieStore.withTxn(trieStore.createTxnWrite())(trieStore.clear)
      _   = initialize(trieStore, branch)
      res <- f(testStore, testSpace)
    } yield {
      try {
        res
      } finally {
        trieStore.close()
        testStore.close()
        env.close()
      }
    })
  }

  override def afterAll(): Unit = {
    dbDir.recursivelyDelete
    super.afterAll()
  }
}

abstract class InMemoryStoreStorageExamplesTestsBase[F[_]]
    extends StorageTestsBase[F, Channel, Pattern, Entry, EntriesCaptor] {

  override def withTestSpace[R](f: (ST, T) => F[R]): R = {

    implicit val cg: Codec[GNAT[Channel, Pattern, Entry, EntriesCaptor]] = codecGNAT(
      serializeChannel.toCodec,
      serializePattern.toCodec,
      serializeInfo.toCodec,
      serializeEntriesCaptor.toCodec
    )

    val branch = Branch("inmem")

    val trieStore =
      InMemoryTrieStore.create[Blake2b256Hash, GNAT[Channel, Pattern, Entry, EntriesCaptor]]()
    val testStore = InMemoryStore.create(trieStore, branch)

    run(for {
      testSpace <- RSpace.create[F, Channel, Pattern, Entry, Entry, EntriesCaptor](
                    testStore,
                    branch
                  )
      //_         <- testStore.withWriteTxnF(testStore.clear)
      //_         <- trieStore.withTxn(trieStore.createTxnWrite())(trieStore.clear).pure[F]
      _   = initialize(trieStore, branch)
      res <- f(testStore, testSpace)
    } yield {
      try {
        res
      } finally {
        trieStore.close()
        testStore.close()
      }
    })
  }
}

abstract class LMDBStoreStorageExamplesTestBase[F[_]]
    extends StorageTestsBase[F, Channel, Pattern, Entry, EntriesCaptor]
    with BeforeAndAfterAll {

  val dbDir: Path    = Files.createTempDirectory("rchain-storage-test-")
  val mapSize: Long  = 1024L * 1024L * 1024L
  val noTls: Boolean = false

  override def withTestSpace[R](f: (ST, T) => F[R]): R = {
    val context   = Context.create[F, Channel, Pattern, Entry, EntriesCaptor](dbDir, mapSize, noTls)
    val testStore = LMDBStore.create[F, Channel, Pattern, Entry, EntriesCaptor](context)
    run(for {
      testSpace <- RSpace.create[F, Channel, Pattern, Entry, Entry, EntriesCaptor](
                    testStore,
                    Branch.MASTER
                  )
      _   <- testStore.withWriteTxnF(testStore.clear)
      res <- f(testStore, testSpace)
    } yield {
      try {
        res
      } finally {
        testStore.close()
        testSpace.close()
        context.close()
      }
    })
  }

  override def afterAll(): Unit = {
    dbDir.recursivelyDelete
    super.afterAll()
  }
}

class InMemoryStoreStorageExamplesTests
    extends InMemoryStoreStorageExamplesTestsBase[Coeval]
    with CoevalTests[Channel, Pattern, Entry, EntriesCaptor]
    with StorageExamplesTests[Coeval]

class MixedInMemoryStoreStorageExamplesTests
    extends MixedInMemoryStoreStorageExamplesTestsBase[Coeval]
    with CoevalTests[Channel, Pattern, Entry, EntriesCaptor]
    with StorageExamplesTests[Coeval]

class LMDBStoreStorageExamplesTest
    extends LMDBStoreStorageExamplesTestBase[Coeval]
    with CoevalTests[Channel, Pattern, Entry, EntriesCaptor]
    with StorageExamplesTests[Coeval]
