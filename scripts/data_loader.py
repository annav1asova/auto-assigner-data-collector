import os
import json
import pandas as pd


def load():
    dirname = os.path.dirname(__file__)
    data_dirname = os.path.join(dirname, 'build_info')

    df_result = pd.DataFrame()
    for filename in os.listdir(data_dirname):
        builds_info_file = open(os.path.join(data_dirname, filename), 'r')
        builds_info = json.loads(builds_info_file.read())
        builds_info_file.close()

        df = convert_to_data_frame(builds_info)
        df_result = pd.concat([df_result, df], axis=0)

    df_result.to_csv('investigations.csv', index=False)

def convert_to_data_frame(builds_info):
    result = {}
    for key in all_keys:
        result[key] = []
    for build in builds_info:
        correct_tests = list(filter(lambda test: "previousResponsible" in test, build['tests']))
        for test in correct_tests:
            row = {**build, **test}
            for key in all_keys:
                if key not in row:
                    result[key].append(None)
                else:
                    result[key].append(row[key])

    return pd.DataFrame(result)


all_keys = ['buildId', 'clientDate', 'committers', 'changeCount', 'comment', 'triggeredBy', 'description',
            'isDefaultBranch', 'branchName', 'changes', 'reasons', 'testCount', 'testRunId',
            'testNameId', 'stacktrace', 'className', 'testMethod', 'testClass', 'testSuite',
            'duration', 'orderId', 'isFixed', 'previousResponsible']

if __name__ == '__main__':
    load()
