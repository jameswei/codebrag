package com.softwaremill.codebrag.dao

import org.scalatest.matchers.ShouldMatchers
import com.softwaremill.codebrag.domain.{ThreadDetails, Followup}
import org.joda.time.DateTime
import pl.softwaremill.common.util.time.FixtureTimeClock
import ObjectIdTestUtils._
import com.foursquare.rogue.LiftRogue._
import com.softwaremill.codebrag.domain.builder.CommitInfoAssembler
import com.softwaremill.codebrag.test.mongo.ClearDataAfterTest
import org.bson.types.ObjectId

class MongoFollowupDAOSpec extends FlatSpecWithMongo with ClearDataAfterTest with ShouldMatchers {

  var followupDao: MongoFollowupDAO = _
  var commitInfoDao: CommitInfoDAO = _

  val Commit = CommitInfoAssembler.randomCommit.get
  val FollowupTargetUserId = oid(12)
  val DifferentUserId1 = oid(15)
  val DifferentUserId2 = oid(14)

  val CommenterName = "John"
  val CommentAuthorId = new ObjectId
  val LikerName = "Mary"
  val CommentId = oid(20)
  val OtherCommentId = oid(30)
  val LikeId = oid(40)

  override def beforeEach() {
    super.beforeEach()
    followupDao = new MongoFollowupDAO
    commitInfoDao = new MongoCommitInfoDAO
    commitInfoDao.storeCommit(Commit)
  }

  it should "create new follow-up for user and commit if one doesn't exist" taggedAs(RequiresDb) in {
    // given
    val now = DateTime.now()
    val followup = Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, now, CommenterName, ThreadDetails(Commit.id))

    // when
    followupDao.createOrUpdateExisting(followup)

    // then
    val allRecords = FollowupRecord.findAll
    allRecords should have size(1)
    val followupFound = allRecords.head
    followupFound.author_id.get should equal(CommentAuthorId)
    followupFound.user_id.get should equal(FollowupTargetUserId)
    followupFound.commit.get.id.get should equal(Commit.id)
    followupFound.reactionId.get should equal(CommentId)
    followupFound.date.get should equal(now.toDate)
  }

  it should "create new inline follow-up if one doesn't exist" taggedAs(RequiresDb) in {
    // given
    val now = DateTime.now()
    val followup = Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, now, CommenterName, ThreadDetails.inline(Commit.id, 20, "file.txt"))

    // when
    followupDao.createOrUpdateExisting(followup)

    // then
    val allRecords = FollowupRecord.findAll
    allRecords should have size(1)
    val followupFound = allRecords.head
    followupFound.author_id.get should equal(CommentAuthorId)
    followupFound.user_id.get should equal(FollowupTargetUserId)
    followupFound.commit.get.id.get should equal(Commit.id)
    followupFound.reactionId.get should equal(CommentId)
    followupFound.date.get should equal(now.toDate)
  }

  it should "update existing follow-up with reaction data when one already exits" taggedAs(RequiresDb) in {
    val createdFollowupId = followupDao.createOrUpdateExisting(Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, ThreadDetails(Commit.id)))

    // when
    val newDate = new FixtureTimeClock(23213213).currentDateTime()
    val newCommenterId = oid(100)
    val newCommentId = oid(200)
    val newCommentAuthorName = "Mary"
    val updatedFollowup = Followup.forComment(newCommentId, newCommenterId, FollowupTargetUserId, newDate, newCommentAuthorName, ThreadDetails(Commit.id))
    followupDao.createOrUpdateExisting(updatedFollowup)


    // then
    val updated = FollowupRecord.findAll.head
    updated.reactionId.get should equal(newCommentId)
    updated.author_id.get should equal(newCommenterId)
    updated.date.get should equal(newDate.toDate)
    updated.lastCommenterName.get should equal(newCommentAuthorName)
    updated.followupId.get should equal(createdFollowupId)
  }

  it should "update follow up only for current thread" taggedAs(RequiresDb) in {
    val baseDate = DateTime.now
    val firstId = followupDao.createOrUpdateExisting(Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id)))
    val secondId = followupDao.createOrUpdateExisting(Followup.forComment(OtherCommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id, Some(20), Some("file.txt"))))

    // when
    val newDate = new FixtureTimeClock(23213213).currentDateTime()
    val newCommentId = oid(123)
    val secondFollowupUpdate = Followup.forComment(newCommentId, CommentAuthorId, FollowupTargetUserId, newDate, CommenterName, ThreadDetails(Commit.id, Some(20), Some("file.txt")))
    followupDao.createOrUpdateExisting(secondFollowupUpdate)


    // then
    val followups = FollowupRecord.where(_.reactionId eqs newCommentId).fetch()
    followups.size should be(1)
    followups.head.date.get should equal(newDate.toDate)
    followups.head.threadId.get.lineNumber.get should equal(Some(20))
    followups.head.threadId.get.fileName.get should equal(Some("file.txt"))
  }

  it should "create new follow up for new inline comments thread" taggedAs(RequiresDb) in {
    val baseDate = DateTime.now
    followupDao.createOrUpdateExisting(Followup.forComment(OtherCommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id, Some(20), Some("file.txt"))))

    // when
    val newCommentId = oid(123)
    val newFollowup = Followup.forComment(newCommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id, Some(30), Some("file.txt")))
    followupDao.createOrUpdateExisting(newFollowup)

    // then
    FollowupRecord.count should be(2)
    val followups = FollowupRecord.where(_.threadId.subselect(_.fileName) eqs "file.txt").and(_.threadId.subselect(_.lineNumber) eqs 30).fetch()
    followups.size should be(1)
  }

  it should "create new follow up for entire commit comments thread if one doesn't exist" taggedAs(RequiresDb) in {
    val baseDate = DateTime.now
    followupDao.createOrUpdateExisting(Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id, Some(20), Some("file.txt"))))

    // when
    val newCommentId = oid(123)
    val newFollowup = Followup.forComment(newCommentId, CommentAuthorId, FollowupTargetUserId, baseDate, CommenterName, ThreadDetails(Commit.id))
    followupDao.createOrUpdateExisting(newFollowup)

    // then
    FollowupRecord.count should be(2)
  }

  it should "delete follow-up for single thread" taggedAs(RequiresDb) in {
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, ThreadDetails(Commit.id, Some(20), Some("file.txt"))))
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, ThreadDetails(Commit.id)))
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, ThreadDetails(Commit.id, Some(23), Some("test.txt"))))

    // when
    val toRemove = FollowupRecord.where(_.threadId.subselect(_.fileName) eqs "test.txt").fetch() // find followup created for text.txt file
    followupDao.delete(toRemove.head.followupId.get)

    // then
    val followupsLeft = FollowupRecord.select(_.threadId.subselect(_.fileName)).fetch()
    followupsLeft.toSet should equal(Set(Some("file.txt"), None))
  }

  it should "not delete follow-ups of other users" taggedAs(RequiresDb) in {
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, DifferentUserId1, DateTime.now, CommenterName, ThreadDetails(Commit.id)))
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, ThreadDetails(Commit.id)))
    followupDao.createOrUpdateExisting(Followup.forComment(Commit.id, CommentAuthorId, DifferentUserId2, DateTime.now, CommenterName, ThreadDetails(Commit.id)))

    // when
    val toRemove = FollowupRecord.where(_.user_id eqs FollowupTargetUserId).fetch() // find followup created for text.txt file
    followupDao.delete(toRemove.head.followupId.get)

    // then
    val storedUserIds = FollowupRecord.findAll.map(_.user_id.get)
    storedUserIds should equal (List(DifferentUserId1, DifferentUserId2))
  }

  it should "create new followup for like when comment followup exists for the same file/line" in {
    // given
    val threadDetails = ThreadDetails(Commit.id, Some(20), Some("file.txt"))
    followupDao.createOrUpdateExisting(Followup.forComment(CommentId, CommentAuthorId, FollowupTargetUserId, DateTime.now, CommenterName, threadDetails))

    // when
    val likeFollowup = Followup.forLike(LikeId, CommentAuthorId, FollowupTargetUserId, DateTime.now, LikerName, threadDetails)
    followupDao.createOrUpdateExisting(likeFollowup)

    // then
    val followups = FollowupRecord.findAll
    followups.length should be(2)
    followups.map(_.threadId.get.fileName.get.get).distinct.head should be("file.txt")
    followups.map(_.lastCommenterName.get).toSet should be(Set(CommenterName, LikerName))
  }
}
