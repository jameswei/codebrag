package com.softwaremill.codebrag.dao.reporting

import com.softwaremill.codebrag.dao._
import org.scalatest.matchers.ShouldMatchers
import com.softwaremill.codebrag.domain.{CommitReviewTask, Authentication, User, CommitInfo}
import org.joda.time.DateTime
import org.bson.types.ObjectId
import com.softwaremill.codebrag.domain.builder.CommitInfoAssembler
import com.softwaremill.codebrag.dao.reporting.views.{CommitView, CommitListView}
import com.softwaremill.codebrag.test.mongo.ClearDataAfterTest
import com.softwaremill.codebrag.common.PagingCriteria


class MongoCommitFinderSpec extends FlatSpecWithMongo with ClearDataAfterTest with ShouldMatchers {

  val commitListFinder = new MongoCommitFinder
  var commitReviewTaskDao = new  MongoCommitReviewTaskDAO
  val commitInfoDao = new MongoCommitInfoDAO

  val userId = ObjectIdTestUtils.oid(123)
  val user = User(userId, Authentication.basic("user", "password"), "John Doe", "john@doe.com", "123", "avatarUrl")
  val DefaultFixturePaging = PagingCriteria(0, 5)

  it should "find all commits to review for given user only" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 5)
    storeCommitReviewTasksFor(userId, storedCommits(0), storedCommits(1))

    // when
    val commitsFound = commitListFinder.findCommitsToReviewForUser(userId, DefaultFixturePaging)

    // then
    commitsFound.commits should have size(2)
  }

  it should "find a page of reviewable commits and count their total number" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 15)
    storeCommitReviewTasksFor(userId, storedCommits.take(14) : _*)

    // when
    val commitsFound = commitListFinder.findCommitsToReviewForUser(userId, DefaultFixturePaging)

    // then
    commitsFound.totalCount should equal(14)
  }

  it should "return correct items for skip 3, limit 2" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 15)
    storeCommitReviewTasksFor(userId, storedCommits.take(14) : _*)

    // when
    val commitsFound = commitListFinder.findCommitsToReviewForUser(userId, PagingCriteria(3, 2))

    // then
    commitsFound.commits.size should equal(2)
    commitsFound.commits(0).id should equal(storedCommits(3).id.toString)
    commitsFound.commits(1).id should equal(storedCommits(4).id.toString)
  }

  it should "return no items for paging criteria beyond actual collection bounds" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 15)
    storeCommitReviewTasksFor(userId, storedCommits.take(14) : _*)

    // when
    val commitsFound = commitListFinder.findCommitsToReviewForUser(userId, PagingCriteria(113, 2))

    // then
    commitsFound.commits should be('empty)
  }

  it should "find all commits for all users" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 5)
    storeCommitReviewTasksFor(userId, storedCommits(0), storedCommits(1))

    // when
    val commitsFound = commitListFinder.findAll(userId)

    // then
    commitsFound.commits should have size(5)
  }

  it should "return correct total number of reviewable commits when fetching all" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 5)
    val commitsToReview = storedCommits.take(2)
    storeCommitReviewTasksFor(userId, commitsToReview : _*)

    // when
    val commitsFound = commitListFinder.findAll(userId)

    // then
    commitsFound.totalCount should equal(commitsToReview.size)
  }

  it should "mark commits that are not pending review" taggedAs(RequiresDb) in {
    // given
    val storedCommits = prepareAndStoreSomeCommits(howMany = 3)
    storeCommitReviewTasksFor(userId, storedCommits(0), storedCommits(1))

    // when
    val commitsFound = commitListFinder.findAll(userId)

    // then
    commitsFound.commits should have size(3)
    foundCommitView(commitsFound, storedCommits, 0).pendingReview should be (true)
    foundCommitView(commitsFound, storedCommits, 1).pendingReview should be (true)
    foundCommitView(commitsFound, storedCommits, 2).pendingReview should be (false)
  }

  it should "find reviewable commits starting from oldest commit date" taggedAs(RequiresDb) in {
    // given
    val baseDate = DateTime.now()
    val olderCommit = CommitInfoAssembler.randomCommit.withSha("111").
      withCommitDate(baseDate).
      withAuthorDate(baseDate.plusSeconds(11)).get
    val newerCommit = CommitInfoAssembler.randomCommit.withSha("222").
      withCommitDate(baseDate.plusSeconds(10)).
      withAuthorDate(baseDate.plusSeconds(10)).get
    commitInfoDao.storeCommit(newerCommit)
    commitInfoDao.storeCommit(olderCommit)
    storeCommitReviewTasksFor(userId, olderCommit, newerCommit)

    // when
    val pendingCommitList = commitListFinder.findCommitsToReviewForUser(userId, DefaultFixturePaging)

    //then
    pendingCommitList.commits.length should equal (2)
    pendingCommitList.commits(0).sha should equal(olderCommit.sha)
    pendingCommitList.commits(1).sha should equal(newerCommit.sha)
  }

  it should "find non-reviewable commits starting from oldest commit date" taggedAs(RequiresDb) in {
    // given
    val baseDate = DateTime.now()
    val olderCommit = CommitInfoAssembler.randomCommit.withSha("111").
      withCommitDate(baseDate).
      withAuthorDate(baseDate.plusSeconds(11)).get
    val newerCommit = CommitInfoAssembler.randomCommit.withSha("222").
      withCommitDate(baseDate.plusSeconds(10)).
      withAuthorDate(baseDate.plusSeconds(10)).get
    commitInfoDao.storeCommit(newerCommit)
    commitInfoDao.storeCommit(olderCommit)

    // when
    val pendingCommitList = commitListFinder.findAll(userId)

    //then
    pendingCommitList.commits.length should equal (2)
    pendingCommitList.commits(0).sha should equal(olderCommit.sha)
    pendingCommitList.commits(1).sha should equal(newerCommit.sha)
  }

  it should "find empty list if there are no commits to review for user" taggedAs(RequiresDb) in {
    // given
    prepareAndStoreSomeCommits(5)

    // when
    val pendingCommitList = commitListFinder.findCommitsToReviewForUser(userId, DefaultFixturePaging)

    //then
    pendingCommitList.commits should be ('empty)
  }

  it should "find commit info (without files) by given id" taggedAs(RequiresDb) in {
    // given
    val commitId = ObjectIdTestUtils.oid(111)
    val commit = CommitInfoAssembler.randomCommit.withId(commitId).withSha("111").withMessage("Commit message").get
    commitInfoDao.storeCommit(commit)

    // when
    val Right(foundCommit) = commitListFinder.findCommitInfoById(commitId.toString, userId)

    //then
    foundCommit.message should equal(commit.message)
    foundCommit.sha should equal(commit.sha)
    foundCommit.pendingReview should be (false)
  }

  it should "mark commit view as pending review if task exists" taggedAs(RequiresDb) in {
    // given
    val commitId = ObjectIdTestUtils.oid(111)
    val commit = CommitInfoAssembler.randomCommit.withId(commitId).withSha("111").withMessage("Commit message").get
    commitInfoDao.storeCommit(commit)
    storeCommitReviewTasksFor(userId, commit)
    // when
    val Right(foundCommit) = commitListFinder.findCommitInfoById(commitId.toString, userId)

    //then
    foundCommit.pendingReview should be (true)
  }

  it should "result with error msg whem commit not found" taggedAs(RequiresDb) in {
    // given
    val nonExistingCommitId = ObjectIdTestUtils.oid(111)

    // when
    val Left(errorMsg) = commitListFinder.findCommitInfoById(nonExistingCommitId.toString, userId)

    //then
    errorMsg should be (s"No such commit $nonExistingCommitId")
  }

  def prepareAndStoreSomeCommits(howMany: Int) = {
    val commitsPrepared = (1 to howMany).map{ i => CommitInfoAssembler.randomCommit.withSha(i.toString).withMessage(s"Commit message $i").get }
    commitsPrepared.foreach{ commitInfoDao.storeCommit(_) }
    commitsPrepared.toList
  }


  def storeCommitReviewTasksFor(userId: ObjectId, commits: CommitInfo*) {
    commits map { commit => CommitReviewTask(commit.id, user.id) } foreach { commitReviewTaskDao.save(_) }
  }

  def foundCommitView(commitsFound: CommitListView, storedCommits: List[CommitInfo], index: Int): CommitView = {
    commitsFound.commits.find(commitView => commitView.id == storedCommits(index).id.toString).get
  }
}
