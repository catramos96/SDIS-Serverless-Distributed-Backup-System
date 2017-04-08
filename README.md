# SDIS-TrabalhosPrat

## Protocolos

### Backup
* descomentar o enhancement

### Restore
* Enhancement -> criar um outro canal só para o envio desta informação particular ? -> alterar o getchunk com um campo que é um endereço particular que abre outra ligação entre o peer que vai mandar um chunk e aquele que pediu. Depois envia-se uma mensagem para multicast de confirmação da recepção. (seria preciso aumentar o randomTime de espera)

### Delete
* Enhancement -> De x em x tempo (thread com runnable la dentro) -> executer, ele vai buscar 1 chunk referente a cada ficheiro nos myChunks e envia uma mensagem para multicast a perguntar se não foi eliminado . O owner peer, se ainda tiver instâncias daquele ficheiro nos stores então sabe que o ficheiro não foi removido, e enviará uma mensagem de resposta em caso de sucesso. Caso x tempo depois de perguntar, o peer não obtenha resposta, elimina todos os chunks do ficheiro. Isto é util caso o peer esteja em baixo quando haja a eliminação de chunks de ficheiros.

### Space Reclaim

### State

## Geral
* Implementar métodos para escolha de acções dependendo das versões.
* Demos para a apresentação.

### Outros
* Considerar a possibilidade de implementar novos tipos de mensagens, apenas para a versão 2 do programa ( com enhancements ) se o caso assim o justificar.
* Por todos os prints para os logs
* Documentação
* Relatório
