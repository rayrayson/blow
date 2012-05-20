
BASE="${SOFT_HOME:-/soft}/x64"

#
# T-Coffee env
#
export DIR_4_TCOFFEE="$BASE/tcoffee"
export MAFFT_BINARIES="$DIR_4_TCOFFEE/plugins/linux/"
export PERL5LIB="$DIR_4_TCOFFEE/perl"
export EMAIL_4_TCOFFEE="tcoffee.msa@gmail.com"
export TMP_4_TCOFFEE="$DIR_4_TCOFFEE/tmp/"
export CACHE_4_TCOFFEE="$DIR_4_TCOFFEE/cache/"
export LOCKDIR_4_TCOFFEE="$DIR_4_TCOFFEE/lck"


#
# RNA-Map required components
#
export PATH="$PATH:$BASE/blast3.pe.linux26-x64"
export PATH="$PATH:$BASE/chr_subseq"
export PATH="$PATH:$BASE/exonerate-2.2.0-x86_64/bin"
export PATH="$PATH:$DIR_4_TCOFFEE/bin"
export PATH="$PATH:$BASE/piper"
export PATH="$PATH:$BASE/R-2.15/bin"
