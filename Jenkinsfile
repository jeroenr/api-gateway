env.jenkinsGitCredentialsId = '10b90f5a-6dd1-4b80-bf6c-5315fad0bc9f'
env.gitUrl = 'git@github.com:cupenya/api-gateway.git'
env.gitBranch = 'deployment-test'


def k8sDefault

stage("Load Build Config") {

fileLoader.withGit('ssh://git@git.cupenya.com:7999/cpy/jenkins-pipelines.git', 'master', '10b90f5a-6dd1-4b80-bf6c-5315fad0bc9f', 'pipeline-git-loader') {
    k8sDefault = fileLoader.load('k8s/default')
  }
}

k8sDefault.bake()
k8sDefault.deploy()
