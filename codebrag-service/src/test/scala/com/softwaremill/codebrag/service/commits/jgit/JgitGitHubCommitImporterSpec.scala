import com.softwaremill.codebrag.dao.{RepositoryHeadStore, CommitInfoDAO}
  var repoHeadStoreMock: RepositoryHeadStore = _
    repoHeadStoreMock = mock[RepositoryHeadStore]
    given(repoHeadStoreMock.get(TestRepoData.remoteUri)).willReturn(Some(lastCommit.getId.name))
      def repoHeadStore = repoHeadStoreMock