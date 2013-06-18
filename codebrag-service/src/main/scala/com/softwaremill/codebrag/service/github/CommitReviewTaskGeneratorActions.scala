package com.softwaremill.codebrag.service.github

import org.joda.time.Interval
import com.softwaremill.codebrag.domain._
import com.softwaremill.codebrag.dao.{CommitInfoDAO, CommitReviewTaskDAO, UserDAO}
import pl.softwaremill.common.util.time.Clock
import com.typesafe.scalalogging.slf4j.Logging
import com.softwaremill.codebrag.domain.CommitsUpdatedEvent
import com.softwaremill.codebrag.domain.CommitReviewTask
import com.softwaremill.codebrag.domain.UpdatedCommit
import com.softwaremill.codebrag.domain.CommitUpdatedEvent._
import com.softwaremill.codebrag.dao.events.NewUserRegistered
import com.softwaremill.codebrag.domain.CommitAuthorClassification._

trait CommitReviewTaskGeneratorActions extends Logging {

  val userDao: UserDAO
  val commitToReviewDao: CommitReviewTaskDAO
  val commitInfoDao: CommitInfoDAO
  val clock: Clock

  def handleNewUserRegistered(event: NewUserRegistered) {
    val commitsToReview = commitInfoDao.findForTimeRange(lastCommitsFetchInterval())
    val tasks = commitsToReview.filterNot(commitAuthoredByUser(_, event)).map(commit => {CommitReviewTask(commit.id, event.id)})
    logger.debug(s"Generating ${tasks.length} tasks for newly registered user: $event")
    tasks.foreach(commitToReviewDao.save(_))
  }

  def handleCommitsUpdated(event: CommitsUpdatedEvent) {
    val commitsToGenerateTasks = if (event.firstTime)
      event.newCommits.withFilter(commit => lastCommitsFetchInterval().contains(commit.commitDate))
    else event.newCommits
    commitsToGenerateTasks.foreach(createAndStoreReviewTasksFor(_))
  }

  private def lastCommitsFetchInterval() = {
    val now = clock.currentDateTime()
    new Interval(now.minusDays(CommitReviewTaskGeneratorActions.LastDaysToFetchCount), now)
  }

  private def createAndStoreReviewTasksFor(commit: UpdatedCommit) {
    val repoUsers = repositoryUsers()
    val tasks = createReviewTasksFor(commit, repoUsers)
    tasks.foreach(commitToReviewDao.save(_))
  }

  // TODO: return only repository users instead of all users as soon as permissions model is implemented
  private def repositoryUsers() = {
    userDao.findAll()
  }

  private def createReviewTasksFor(commit: UpdatedCommit, users: List[User]) = {
    users.filterNot(commitAuthoredByUser(commit, _)).map(user => CommitReviewTask(commit.id, user.id))
  }

}

object CommitReviewTaskGeneratorActions {

  val LastDaysToFetchCount = 3
}