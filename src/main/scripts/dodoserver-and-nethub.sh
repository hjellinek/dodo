#!/bin/bash

if [ -f ${HOMEDIR}/dodo_nethub.pids ]; then
  kill $(cat ${HOMEDIR}/dodo_nethub.pids) > /dev/null 2>&1
fi

mkdir -p ${HOMEDIR}/logs

rm -f ${HOMEDIR}/logs/*.{log,err}

sed "s#\${HOMEDIR}#${HOMEDIR}#g"                 \
    < ${HOMEDIR}/config/dodo.properties.template      \
    > ${HOMEDIR}/config/dodo.properties

java -jar ${HOMEDIR}/build/libs/dodo-1.0.jar \
         >> ${HOMEDIR}/logs/nethub.log       \
         2>> ${HOMEDIR}/logs/nethub.err      &

nethub_pid=$!

java ${DEBUG_DODO} -cp ${HOMEDIR}/build/libs/dodo-1.0.jar \
         dev.hawala.xns.DodoServer                        \
         ${HOMEDIR}/config/dodo.properties                \
         -machinecfg:${HOMEDIR}/config/machines.cfg       \
         >> ${HOMEDIR}/logs/dodoserver.log                \
         2>> ${HOMEDIR}/logs/dodoserver.err &

dodo_pid=$!

echo $nethub_pid $dodo_pid > ${HOMEDIR}/dodo_nethub.pids
# write the DodoServer pid to its own file so we can send SIGHUP easily
echo $dodo_pid > ${HOMEDIR}/dodo.pid
