package event_sourcing

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import event_sourcing.PersistenceBehavior._
import org.scalatest.WordSpecLike

/**
  * Created by kartik on Feb 27, 2020
  */

class PersistenceBehaviorTest extends ScalaTestWithActorTestKit(
  s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot"
    """) with WordSpecLike {

  "Persistence behavior" should {

    "add data" in {
      val persistenceBehavior = testKit.spawn(PersistenceBehavior("Behavior1"))
      val probe               = testKit.createTestProbe[PersistenceBehavior.DataStatus]

      val mineoneData = AddData("MineOne", probe.ref)
      persistenceBehavior ! mineoneData

      probe.expectMessage(DataAccepted(mineoneData.data)) // probe.expectMessageType[DataAccepted]

      val commonData = AddData("Common", probe.ref)
      persistenceBehavior ! commonData

      probe.expectMessage(DataAccepted(commonData.data))

      persistenceBehavior ! GetState(probe.ref)
      probe.expectMessage(DataHistory(Seq(commonData.data, mineoneData.data))) // probe.expectMessageType[DataHistory]
    }

    "clear data" in {
      val persistenceBehavior = testKit.spawn(PersistenceBehavior("Behavior2"))
      val probe               = testKit.createTestProbe[PersistenceBehavior.DataStatus]
      val clearData           = ClearData(probe.ref)
      persistenceBehavior ! clearData
      probe.expectMessage(DataReset)
    }

    "keep its state" in {
      val behavior            = PersistenceBehavior("Behavior3")
      val persistenceBehavior = testKit.spawn(behavior)
      val probe               = testKit.createTestProbe[PersistenceBehavior.DataStatus]

      val mineoneData = AddData("MineOne", probe.ref)
      persistenceBehavior ! mineoneData

      probe.expectMessage(DataAccepted(mineoneData.data)) // probe.expectMessageType[DataAccepted]

      val commonData = AddData("Common", probe.ref)
      persistenceBehavior ! commonData

      probe.expectMessage(DataAccepted(commonData.data))

      testKit.stop(persistenceBehavior)

      val restartedPersistenceBehavior = testKit.spawn(behavior)
      restartedPersistenceBehavior ! GetState(probe.ref)
      probe.expectMessage(DataHistory(Seq(commonData.data, mineoneData.data)))
    }
  }
}
