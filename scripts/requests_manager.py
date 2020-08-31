import requests
import json
import os
import logging
import argparse
import time


def load_build_ids(host, project_id, token, timeout=10800):
    response = requests.get(f'{host}/app/rest/builds/?locator=affectedProject:{project_id},start:11300,count:100',
                            headers={'Authorization': token, 'Accept': 'application/json'}, timeout=timeout)

    response_json = json.loads(response.text)
    build_ids = list(map(lambda t: t['id'], response_json['build']))
    i = 0
    while "nextHref" in response_json and i < 10:
        i += 1
        print(i)
        response = requests.get(f'{host}{response_json["nextHref"]}',
                                headers={'Authorization': token, 'Accept': 'application/json'}, timeout=timeout)
        response_json = json.loads(response.text)
        build_ids += list(map(lambda t: t['id'], response_json['build']))

    dirname = os.path.dirname(__file__)
    file_ids = open(os.path.join(dirname, 'build_ids.json'), 'w')
    file_ids.write(str(build_ids))
    file_ids.close()
    logging.info(f'Build ids received')


def load_build_test_ids(host, project_id, token, timeout=10800):
    response = requests.get(f'{host}/buildTestIdsCollector.html',
                            params={'projectExternalId': project_id},
                            headers={'Authorization': token}, timeout=timeout)

    response_json = json.loads(response.text)
    dirname = os.path.dirname(__file__)
    file_ids = open(os.path.join(dirname, 'build_test_ids.json'), 'w')
    file_ids.write(str(response_json))
    file_ids.close()
    logging.info(f'Build test ids received')


def load_builds_info(host, project_id, token, start_from=0, batch_size=5, timeout=10800):
    dirname = os.path.dirname(__file__)
    file_ids = open(os.path.join(dirname, 'build_test_ids.json'), 'r')
    build_test_ids = json.loads(file_ids.read())
    file_ids.close()

    for i in range(start_from, len(build_test_ids), batch_size):
        batch = build_test_ids[i:i + batch_size]

        time_before = time.time()
        response = requests.get(f'{host}/assignInfoCollector.html',
                                params={'projectExternalId': project_id,
                                        'ids': ','.join(map(lambda pair: f'{pair["buildId"]}_{pair["testId"]}', batch))},
                                headers={'Authorization': token}, timeout=timeout)

        if not response.ok:
            logging.info(f'Error: status code {response.status_code}')
        time_after = time.time()
        file_info = open(os.path.join(dirname, 'build_info', f'builds_info_{i}_{i + batch_size - 1}.json'), 'w')
        file_info.write(response.text)
        file_info.close()
        logging.info(f'Finished batch from {i} to {i + batch_size - 1} in {time_after - time_before} seconds')


# def load_builds_info(host, project_id, token, start_from=0, batch_size=5, timeout=10800):
#     dirname = os.path.dirname(__file__)
#     file_ids = open(os.path.join(dirname, 'build_ids.json'), 'r')
#     build_ids = json.loads(file_ids.read())
#     file_ids.close()
#
#     for i in range(start_from, len(build_ids), batch_size):
#         batch = build_ids[i:i + batch_size]
#
#         time_before = time.time()
#         response = requests.get(f'{host}/assignInfoCollector.html',
#                                 params={'projectExternalId': project_id, 'ids': ','.join(map(str, batch))},
#                                 headers={'Authorization': token}, timeout=timeout)
#
#         if not response.ok:
#             logging.info(f'Error: status code {response.status_code}')
#         time_after = time.time()
#         file_info = open(os.path.join(dirname, 'build_info', f'builds_info_{i}_{i + batch_size - 1}.json'), 'w')
#         file_info.write(response.text)
#         file_info.close()
#         logging.info(f'Finished batch from {i} to {i + batch_size - 1} in {time_after - time_before} seconds')


def parse_args():
    parser = argparse.ArgumentParser(
        description='Download build info from Teamcity Server',
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument('-auth_token', help='authentication token')
    parser.add_argument('-host', help='host address')
    parser.add_argument('-project_id', help='external project id')

    subparsers = parser.add_subparsers(dest='request_type', help='types of request')

    build_ids_parser = subparsers.add_parser('build_ids')
    build_info_parser = subparsers.add_parser('builds_info')

    build_info_parser.add_argument('-start_from', default=0, help='start position for request')
    build_info_parser.add_argument('-batch_size', default=5, help='batch size')

    return parser.parse_args()


def main(args):
    logging.basicConfig(filename='data_loader.log', level=logging.DEBUG)

    if args.request_type == 'build_ids':
        load_build_test_ids(args.host, args.project_id, args.auth_token)
    elif args.request_type == 'builds_info':
        load_builds_info(args.host, args.project_id, args.auth_token,
                         start_from=int(args.start_from),
                         batch_size=int(args.batch_size))


if __name__ == '__main__':
    main(parse_args())
