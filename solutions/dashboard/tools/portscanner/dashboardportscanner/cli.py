from argparse import ArgumentParser
from dashboardportscanner.main import Main


def handle_cli():
    """
    Application's entry point.
    Here, application's settings are read from the command line,
    environment variables and CRD. Then, retrieving and processing
    of Kubernetes events are initiated.
    """
    parser = ArgumentParser(
        description='CortxPortScanner - The Port Scanner',
        prog='cortxportscanner'
    )
    parser.add_argument(
        '--namespace',
        type=str,
        default='cortx',
        help='Operator Namespace, default: cortx'
    )
    parser.add_argument(
        '--cr-name',
        type=str,
        default='cortx-port-scanner',
        help='CR Name, cortx-port-scanner'
    )

    args = parser.parse_args()

    main = Main(args=args)
    main.load_kubernetes()
    main.main()
