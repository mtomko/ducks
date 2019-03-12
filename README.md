# fastq-dmux
FASTQ demultiplexer

## Usage
    Usage: fastq-dmux --conditions <path> --dmux-fastq <path> --data-fastq <path> [--output-dir <path>]
    
    Demultiplexes FASTQ files based on conditions
    
    Options and flags:
        --help
            Display this help text.
        --conditions <path>
            The conditions file
        --dmux-fastq <path>, -m <path>
            The FASTQ file containing demultiplexing reads
        --data-fastq <path>, -d <path>
            The FASTQ file containing data reads
        --output-dir <path>, -o <path>
            The output directory
