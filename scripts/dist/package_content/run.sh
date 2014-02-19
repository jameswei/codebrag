#!/bin/bash

function java_not_installed {
  local JAVA_VERSION_REQUIRED=7
  local JAVA_VERSION_CURRENT=`java -version 2>&1 | grep "java version" | awk '{print $3}' | tr -d \" | awk '{split($0,numbers,"."); print numbers[2]}'`
  if [[ -z $JAVA_VERSION_CURRENT || $JAVA_VERSION_CURRENT -lt $JAVA_VERSION_REQUIRED ]]; then
    echo "ERROR: Java in required version $JAVA_VERSION_REQUIRED not found" >&2
    echo 0
  else
    echo 1
  fi
}

# 0. check if REPOS_DIR defined in config and set it accordingly 
# 1. check if root dir with repos exists
# 2. check if it contains exactly 1 repo dir
# 3. check if repo dir is readable by current user
# 4. check if it is Git repo (check for .git dir)
# 5. check if repo dir is writable by current user
function check_repo_available {
  local CWD=$(pwd)

  # check if repos-root defined in config and set REPOS_DIR accordingly
  local REPOS_DIR=$(grep '^\s*repos-root\s*=' codebrag.conf | grep -o '".*"' | sed 's/"//g')
  if [ -z "$REPOS_DIR" ]; then
    REPOS_DIR=$CWD/repos
  fi

  # check if "repos" exist
  # TODO: read config in case of "repos" set to non-default
  if [ ! -d "$REPOS_DIR" ]; then
    echo "ERROR: Cannot find directory containing your repository: $REPOS_DIR"
    exit 1
  fi

  # check if at least 1 repository directory is present
  local SUBDIRS_COUNT=`echo $(find $REPOS_DIR -maxdepth 1 -mindepth 1 -type d -print | wc -l)`
  if [ $SUBDIRS_COUNT -eq 0 ]; then
    echo "ERROR: $REPOS_DIR should contain directory with your repository. Clone your repository into $REPOS_DIR:
 git: git clone git://yourcompany.com/path/to/project-abc.git
 svn: git svn clone git://yourcompany.com/path/to/project-abc.git    # don't use 'svn checkout'"
    exit 1    
  fi

  # check if exactly 1 repository directory is present
  if [ $SUBDIRS_COUNT -ne 1 ]; then
    echo "ERROR: $REPOS_DIR directory should contain exactly one directory with your repository ($SUBDIRS_COUNT directories found).
 At the moment Codebrag supports single repository configuration only."
    exit 1
  fi

  local POTENTIAL_REPO_DIR=$(find $REPOS_DIR -maxdepth 1 -mindepth 1 -type d -print)

  # check if repo dir is readable by current user
  if [ ! -r "$POTENTIAL_REPO_DIR" ]; then
    echo "ERROR: Looks like $POTENTIAL_REPO_DIR is not readable"
    exit 1    
  fi

  # check if dir is git repo (contains .git dir)
  local GIT_DIR=$POTENTIAL_REPO_DIR/.git
  if [ ! -d "$GIT_DIR" ]; then
    echo "ERROR: Looks like $POTENTIAL_REPO_DIR is not a git repository"
    exit 1    
  fi

  # check if repo dir is writable by current user
  if [ ! -w "$POTENTIAL_REPO_DIR" ]; then
    echo "ERROR: Looks like $POTENTIAL_REPO_DIR is not writable"
    exit 1    
  fi
}




#######################
### CHECK PRECONDITIONS
#######################

if [[ $(java_not_installed) -eq 0 ]]; then
  echo "Please install required version of Java first"
  exit 1
fi
check_repo_available

#######################
### RUN CODEBRAG
#######################

CONFIG=${1:-"codebrag.conf"}

echo "Starting Codebrag... (with config:$CONFIG)"
nohup java -Dfile.encoding=UTF-8 -Dconfig.file=./$CONFIG -Dlogback.configurationFile=./logback.xml -jar codebrag.jar &
echo "Codebrag started. Logs are written to codebrag.log"
