def finalYaml = new File(basedir, 'target/final.yaml')
assert finalYaml.exists() : "File does not exist: $finalYaml}"
def expectedYaml = new File(basedir, 'expected.yaml')
assert finalYaml.getText('UTF-8').equals(expectedYaml.getText('UTF-8')) : "Unexpected content of: $finalYaml}"