# Ducks
FASTQ demultiplexer

## Usage
    Usage: ducks --conditions <path> --dmux-fastq <path> --data-fastq <path> [--output-dir <path>]
    
    Demultiplexes FASTQ files based on conditions
    
    Options and flags:
        --help
            Display this help text.
        --conditions <path>, -c <path>
            The conditions file
        --dmux-fastq <path>, -1 <path>
            The FASTQ file containing demultiplexing reads
        --data-fastq <path>, -2 <path>
            The FASTQ file containing data reads
        --output-dir <path>, -o <path>
            The output directory
